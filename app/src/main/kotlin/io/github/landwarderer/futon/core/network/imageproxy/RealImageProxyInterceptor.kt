package io.github.landwarderer.futon.core.network.imageproxy

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.observeAsStateFlow
import io.github.landwarderer.futon.core.util.ext.processLifecycleScope
import io.github.landwarderer.futon.mihon.compat.MihonRequestContext
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealImageProxyInterceptor @Inject constructor(
    private val settings: AppSettings,
) : ImageProxyInterceptor {

    private val delegate = settings.observeAsStateFlow(
        scope = processLifecycleScope + Dispatchers.IO,
        key = AppSettings.KEY_IMAGES_PROXY,
        valueProducer = { createDelegate() },
    )

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        return delegate.value?.intercept(chain) ?: chain.proceed()
    }

    override suspend fun interceptPageRequest(request: Request, okHttp: OkHttpClient): Response {
        val (effectiveRequest, effectiveClient) = adaptMihonImageRequest(request, okHttp)
        return delegate.value?.interceptPageRequest(effectiveRequest, effectiveClient)
            ?: effectiveClient.newCall(effectiveRequest).await()
    }

    /**
     * PageLoader and DownloadWorker historically create a generic tagged image GET. Recover the
     * real Mihon HttpSource at this shared boundary so offline downloads also use the extension's
     * imageRequest() headers/interceptors/client. Reader requests that already came from the
     * repository are left untouched and simply use the client supplied by that repository.
     */
    private fun adaptMihonImageRequest(
        request: Request,
        fallbackClient: OkHttpClient,
    ): Pair<Request, OkHttpClient> {
        val source = request.tag(MangaSource::class.java) as? MihonMangaSource
            ?: return request to fallbackClient
        val httpSource = source.catalogueSource as? HttpSource
            ?: return request to fallbackClient

        val sourceRequest = runCatching {
            MihonRequestContext.withSourceBlocking(source) {
                httpSource.imageRequest(
                    Page(
                        index = 0,
                        url = request.url.toString(),
                        imageUrl = request.url.toString(),
                    ),
                )
            }
        }.getOrDefault(request)

        val taggedRequest = sourceRequest.newBuilder()
            .tag(MangaSource::class.java, source)
            .tag(ContentSource::class.java, source)
            .build()
        val sourceClient = runCatching {
            MihonRequestContext.withSourceBlocking(source) { httpSource.client }
        }.getOrDefault(fallbackClient)

        return taggedRequest to sourceClient
    }

    private fun createDelegate(): ImageProxyInterceptor? = when (val proxy = settings.imagesProxy) {
        -1 -> null
        0 -> WsrvNlProxyInterceptor()
        1 -> ZeroMsProxyInterceptor()
        else -> error("Unsupported images proxy $proxy")
    }
}
