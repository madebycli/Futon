// Cover request/client behavior adapted from Kototoro at c1128b91140053b081cc7453c87a16f52ab2f12a.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.image

import coil3.ImageLoader
import coil3.Uri as CoilUri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.network.HttpException
import coil3.network.NetworkHeaders
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.request.Options
import io.github.landwarderer.futon.core.exceptions.CloudFlareException
import io.github.landwarderer.futon.core.parser.MangaRepository
import io.github.landwarderer.futon.core.util.ext.mangaSourceKey
import io.github.landwarderer.futon.core.util.ext.toMimeTypeOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.parsers.util.await
import javax.inject.Inject

/**
 * Coil fetcher for covers belonging to a repository that owns its image client.
 *
 * Today that means Mihon HttpSource. Registering this before Coil's generic OkHttp fetcher keeps
 * extension-specific Referer/cookie/TLS/interceptor behavior for list and details covers, matching
 * the current Kototoro integration instead of only fixing reader pages and offline downloads.
 */
class MihonCoverFetcher(
    private val imageUrl: String,
    private val options: Options,
    private val imageClient: OkHttpClient,
    private val repo: MangaRepository,
    private val diskCache: DiskCache?,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val diskCacheKey = options.diskCacheKey ?: imageUrl
        readFromDiskCache(diskCacheKey)?.let { return it }
        if (diskCache == null || !options.diskCachePolicy.writeEnabled) {
            return fetchFromNetwork()
        }
        return fetchFromNetworkAndCache(diskCacheKey)
    }

    private suspend fun fetchFromNetworkAndCache(diskCacheKey: String): FetchResult {
        val response = executeNetworkRequest()
        val mimeType = response.mimeType?.toMimeTypeOrNull()?.toString()
        val editor = diskCache?.openEditor(diskCacheKey)
        if (editor == null) {
            return response.toFetchResult(mimeType)
        }
        try {
            if (mimeType != null) {
                diskCache.fileSystem.write(editor.metadata) {
                    writeUtf8(mimeType)
                }
            }
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            response.close()
            val snapshot = checkNotNull(editor.commitAndOpenSnapshot()) {
                "Failed to open cached Mihon cover snapshot"
            }
            return snapshot.toFetchResult(mimeType, DataSource.NETWORK, diskCacheKey)
        } catch (e: Exception) {
            response.close()
            runCatching { editor.abort() }
            throw e
        }
    }

    private suspend fun fetchFromNetwork(): FetchResult {
        val response = executeNetworkRequest()
        val mimeType = response.mimeType?.toMimeTypeOrNull()?.toString()
        return response.toFetchResult(mimeType)
    }

    private suspend fun executeNetworkRequest(): okhttp3.Response {
        val request = repo.buildCoverRequest(imageUrl)
            ?: Request.Builder().url(imageUrl).build()
        val response = try {
            imageClient.newCall(request).await()
        } catch (_: CloudFlareException) {
            // A cover CDN challenge should not launch Futon's interactive captcha flow repeatedly.
            throw HttpException(
                NetworkResponse(
                    code = 403,
                    requestMillis = System.currentTimeMillis(),
                    responseMillis = System.currentTimeMillis(),
                    headers = NetworkHeaders.Builder().build(),
                    body = null,
                    delegate = okhttp3.Response.Builder()
                        .request(request)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .message("CloudFlare Protected CDN")
                        .code(403)
                        .build(),
                ),
            )
        }
        if (!response.isSuccessful) {
            val networkResponse = response.toNetworkResponse()
            response.close()
            throw HttpException(networkResponse)
        }
        return response
    }

    private fun okhttp3.Response.toFetchResult(mimeType: String?): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                source = body.source(),
                fileSystem = options.fileSystem,
            ),
            mimeType = mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun readFromDiskCache(diskCacheKey: String): FetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val cache = diskCache ?: return null
        val snapshot = cache.openSnapshot(diskCacheKey) ?: return null
        val mimeType = runCatching {
            cache.fileSystem.read(snapshot.metadata) { readUtf8() }
                .trim()
                .takeIf(String::isNotEmpty)
        }.getOrNull()
        return snapshot.toFetchResult(mimeType ?: "image/*", DataSource.DISK, diskCacheKey)
    }

    private fun DiskCache.Snapshot.toFetchResult(
        mimeType: String?,
        dataSource: DataSource,
        diskCacheKey: String,
    ): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = data,
                fileSystem = diskCache!!.fileSystem,
                diskCacheKey = diskCacheKey,
                closeable = this,
            ),
            mimeType = mimeType,
            dataSource = dataSource,
        )
    }

    private val okhttp3.Response.mimeType: String?
        get() = header("Content-Type") ?: body.contentType()?.toString()

    private fun okhttp3.Response.toNetworkResponse() = NetworkResponse(
        code = code,
        requestMillis = sentRequestAtMillis,
        responseMillis = receivedResponseAtMillis,
        headers = headers.toNetworkHeaders(),
        body = body.source().let(::NetworkResponseBody),
        delegate = this,
    )

    private fun Headers.toNetworkHeaders(): NetworkHeaders {
        val result = NetworkHeaders.Builder()
        for ((key, values) in this) {
            result.add(key, values)
        }
        return result.build()
    }

    class Factory @Inject constructor(
        private val mangaRepositoryFactory: MangaRepository.Factory,
    ) : Fetcher.Factory<CoilUri> {

        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            if (scheme != "http" && scheme != "https") return null

            val mangaSource = options.extras[mangaSourceKey] ?: return null
            val repo = mangaRepositoryFactory.create(mangaSource)
            val imageClient = repo.imageRequestClient() ?: return null

            return MihonCoverFetcher(
                imageUrl = data.toString(),
                options = options,
                imageClient = imageClient,
                repo = repo,
                diskCache = imageLoader.diskCache,
            )
        }
    }
}
