// Mihon extension-store protocol adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.extensions.repo

import android.util.Log
import androidx.annotation.Keep
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.landwarderer.futon.core.network.MangaHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.prefs.GitHubMirror
import io.github.landwarderer.futon.mihon.MihonExtensionLoader
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class ExtensionRepoService @Inject constructor(
    @MangaHttpClient private val httpClient: OkHttpClient,
    private val json: Json,
    private val settings: AppSettings,
) {

    private val protoBuf = ProtoBuf {
        @Suppress("OPT_IN_USAGE")
        encodeDefaults = true
    }

    private fun applyMirror(input: String): String {
        // raw.github.com has been retired. Treat it only as a legacy stored value.
        val url = input.replace("https://raw.github.com/", "https://raw.githubusercontent.com/")
        if (url.startsWith("https://raw.githubusercontent.com/")) {
            return when (settings.gitHubMirror) {
                GitHubMirror.NATIVE, GitHubMirror.KEIYOUSHI -> url
                GitHubMirror.KKGITHUB -> url.replace("raw.githubusercontent.com", "raw.kkgithub.com")
                GitHubMirror.GHPROXY -> "https://mirror.ghproxy.com/$url"
                GitHubMirror.GHPROXY_NET -> "https://ghproxy.net/$url"
            }
        }
        if (url.startsWith("https://github.com/")) {
            return when (settings.gitHubMirror) {
                GitHubMirror.KKGITHUB -> url.replace("github.com", "kkgithub.com")
                GitHubMirror.GHPROXY -> "https://mirror.ghproxy.com/$url"
                GitHubMirror.GHPROXY_NET -> "https://ghproxy.net/$url"
                else -> url
            }
        }
        return url
    }

    private fun deriveRepoName(baseUrl: String, defaultName: String): String {
        val url = baseUrl.toHttpUrlOrNull() ?: return defaultName
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        return when {
            segments.size >= 2 && url.host.contains("githubusercontent.com") -> "${segments[0]}/${segments[1]}"
            segments.size >= 2 && url.host == "github.com" -> "${segments[0]}/${segments[1]}"
            segments.isNotEmpty() -> segments.last()
            else -> url.host
        }
    }

    suspend fun fetchRepoDetails(baseUrl: String, type: ExternalExtensionType): ExternalExtensionRepo {
        if (type == ExternalExtensionType.IREADER || type == ExternalExtensionType.JAR) {
            val now = System.currentTimeMillis()
            val derived = deriveRepoName(baseUrl, if (type == ExternalExtensionType.IREADER) "IReader" else "Futon")
            val repoName = if (type == ExternalExtensionType.IREADER) "IReader: $derived" else "Futon: $derived"
            var version: String? = null
            if (type == ExternalExtensionType.JAR) {
                runCatching {
                    withTimeout(REPO_DETAILS_TIMEOUT_MS) {
                        val body = httpClient.newCall(GET(applyMirror("$baseUrl/index.min.json"))).awaitSuccess().use {
                            it.body.string()
                        }
                        version = json.decodeFromString<List<ExtensionIndexDto>>(body).firstOrNull()?.version
                    }
                }
            }
            return ExternalExtensionRepo(
                type = type,
                baseUrl = baseUrl,
                name = repoName,
                shortName = derived,
                website = baseUrl,
                signingKeyFingerprint = baseUrl.hashCode().toString(16),
                createdAt = now,
                updatedAt = now,
                lastSuccessAt = now,
                lastError = null,
                version = version,
            )
        }

        if (isProtobufIndexUrl(baseUrl)) {
            return repoFromStoreIndex(baseUrl, type, fetchExtensionStoreIndex(baseUrl))
        }

        val repoJsonUrl = applyMirror("${baseUrl.trimEnd('/')}/repo.json")
        val startedAt = System.currentTimeMillis()
        Log.d(TAG, "fetchRepoDetails:start type=$type url=$repoJsonUrl")
        return withTimeout(REPO_DETAILS_TIMEOUT_MS) {
            val body = runCatching {
                httpClient.newCall(GET(repoJsonUrl)).awaitSuccess().use { response -> response.body.string() }
            }.getOrElse { error ->
                if (type == ExternalExtensionType.MIHON || type == ExternalExtensionType.ANIYOMI) {
                    return@withTimeout fetchRepoDetails("${baseUrl.trimEnd('/')}/index.pb", type)
                }
                throw error
            }
            val dto = json.decodeFromString<RepoMetaWrapperDto>(body)
            dto.indexV2?.takeIf { it.isNotBlank() }?.let { indexV2 ->
                val resolvedIndexUrl = repoJsonUrl.toHttpUrlOrNull()?.resolve(indexV2)?.toString() ?: indexV2
                return@withTimeout fetchRepoDetails(resolvedIndexUrl, type)
            }
            val now = System.currentTimeMillis()
            ExternalExtensionRepo(
                type = type,
                baseUrl = baseUrl,
                name = dto.meta.name,
                shortName = dto.meta.shortName,
                website = dto.meta.website,
                signingKeyFingerprint = dto.meta.signingKeyFingerprint,
                createdAt = now,
                updatedAt = now,
                lastSuccessAt = now,
                lastError = null,
            )
        }.also { repo ->
            Log.d(TAG, "fetchRepoDetails:success type=$type baseUrl=${repo.baseUrl} name=${repo.displayName} elapsedMs=${System.currentTimeMillis() - startedAt}")
        }
    }

    suspend fun fetchAvailableExtensions(repo: ExternalExtensionRepo): List<RepoAvailableExtension> =
        runCatching { fetchAvailableExtensionsOrThrow(repo) }.getOrDefault(emptyList())

    suspend fun fetchAvailableExtensionsOrThrow(repo: ExternalExtensionRepo): List<RepoAvailableExtension> {
        if (isProtobufIndexUrl(repo.baseUrl)) {
            return fetchProtobufExtensions(repo, repo.baseUrl)
        }

        val startedAt = System.currentTimeMillis()
        Log.d(TAG, "fetchAvailableExtensions:start type=${repo.type} baseUrl=${repo.baseUrl}")
        if (repo.type == ExternalExtensionType.MIHON || repo.type == ExternalExtensionType.ANIYOMI) {
            val protobufUrl = "${repo.baseUrl.trimEnd('/')}/index.pb"
            try {
                return fetchProtobufExtensions(repo, protobufUrl)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.d(TAG, "fetchAvailableExtensions:protobuf fallback to json, reason=${error.message}")
            }
        }

        val requestUrl = applyMirror("${repo.baseUrl.trimEnd('/')}/index.min.json")
        Log.d(TAG, "fetchAvailableExtensions:trying json url=$requestUrl")
        return try {
            val extensions = withTimeout(CATALOG_TIMEOUT_MS) {
                val body = httpClient.newCall(GET(requestUrl)).awaitSuccess().use { response -> response.body.string() }
                if (repo.type == ExternalExtensionType.IREADER) {
                    json.decodeFromString<List<IReaderExtensionIndexDto>>(body)
                        .map { it.toAvailableExtension(repo) }
                } else {
                    json.decodeFromString<List<ExtensionIndexDto>>(body)
                        .mapNotNull { it.toAvailableExtension(repo) }
                }
            }
            Log.d(TAG, "fetchAvailableExtensions:success (json) type=${repo.type} baseUrl=${repo.baseUrl} count=${extensions.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
            extensions
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(TAG, "fetchAvailableExtensions:failed type=${repo.type} baseUrl=${repo.baseUrl} elapsedMs=${System.currentTimeMillis() - startedAt} message=${error.message}", error)
            throw error
        }
    }

    fun normalizeIndexUrl(input: String): String? {
        var url = input.trim()
            .replace("https://raw.github.com/", "https://raw.githubusercontent.com/")
            .toHttpUrlOrNull() ?: return null
        if (url.scheme != "https") return null

        // Accept copied GitHub raw/tree/blob URLs and convert them to the stable raw host.
        if (url.host == "github.com") {
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            if (segments.size >= 3 && segments[2] in setOf("raw", "tree", "blob", "refs")) {
                val user = segments[0]
                val repo = segments[1]
                val rest = if (segments[2] == "refs" && segments.size >= 5 && segments[3] == "heads") {
                    segments.drop(4)
                } else {
                    segments.drop(3)
                }
                url = url.newBuilder()
                    .host("raw.githubusercontent.com")
                    .encodedPath("/$user/$repo/${rest.joinToString("/")}")
                    .build()
            }
        }

        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        when (segments.lastOrNull()?.lowercase()) {
            "index.pb", "index.min.json", "repo.json" -> Unit
            "index.json" -> segments[segments.lastIndex] = "index.min.json"
            else -> segments += "index.min.json"
        }
        return url.newBuilder()
            .encodedPath("/" + segments.joinToString("/"))
            .fragment(null)
            .query(null)
            .build()
            .toString()
    }

    fun baseUrlFromIndexUrl(indexUrl: String): String {
        val normalized = indexUrl.toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.query(null)?.build()?.toString() ?: indexUrl
        if (isProtobufIndexUrl(normalized)) return normalized
        return normalized.removeSuffix("/index.min.json").removeSuffix("/repo.json")
    }

    private suspend fun fetchProtobufExtensions(
        repo: ExternalExtensionRepo,
        indexUrl: String,
    ): List<RepoAvailableExtension> {
        val startedAt = System.currentTimeMillis()
        Log.d(TAG, "fetchAvailableExtensions:trying protobuf url=$indexUrl")
        return try {
            val extensions = withTimeout(CATALOG_TIMEOUT_MS) {
                val index = fetchExtensionStoreIndex(indexUrl)
                val list = index.extensionList ?: index.extensionListUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { fetchExtensionList(resolveUrl(indexUrl, it)) }
                    ?: error("Extension store does not contain an extension list")
                list.extensions.asSequence()
                    .filterNot(::isOutdatedExtension)
                    .mapNotNull { it.toAvailableExtension(repo, indexUrl) }
                    .toList()
            }
            Log.d(TAG, "fetchAvailableExtensions:success (protobuf) type=${repo.type} baseUrl=${repo.baseUrl} count=${extensions.size} elapsedMs=${System.currentTimeMillis() - startedAt}")
            extensions
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(TAG, "fetchAvailableExtensions:protobuf failed type=${repo.type} indexUrl=$indexUrl message=${error.message}", error)
            throw error
        }
    }

    private suspend fun fetchExtensionStoreIndex(indexUrl: String): NetworkExtensionStoreDto {
        val bytes = fetchProtobufBytes(indexUrl)
        return protoBuf.decodeFromByteArray(bytes)
    }

    private suspend fun fetchExtensionList(listUrl: String): ExtensionListDto {
        val bytes = fetchProtobufBytes(listUrl)
        return protoBuf.decodeFromByteArray(bytes)
    }

    private suspend fun fetchProtobufBytes(url: String): ByteArray {
        val raw = httpClient.newCall(GET(applyMirror(url))).awaitSuccess().use { it.body.bytes() }
        return if (raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
        } else {
            raw
        }
    }

    private fun repoFromStoreIndex(
        indexUrl: String,
        type: ExternalExtensionType,
        index: NetworkExtensionStoreDto,
    ): ExternalExtensionRepo {
        val now = System.currentTimeMillis()
        return ExternalExtensionRepo(
            type = type,
            baseUrl = indexUrl,
            name = index.name.ifBlank { deriveRepoName(indexUrl, "Extensions") },
            shortName = index.badgeLabel.takeIf { it.isNotBlank() },
            website = index.contact?.website?.takeIf { it.isNotBlank() } ?: indexUrl,
            signingKeyFingerprint = index.signingKey,
            createdAt = now,
            updatedAt = now,
            lastSuccessAt = now,
            lastError = null,
        )
    }

    private fun resolveUrl(baseUrl: String, value: String): String {
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        return baseUrl.toHttpUrlOrNull()?.resolve(value)?.toString() ?: value
    }

    private fun isProtobufIndexUrl(url: String): Boolean =
        url.toHttpUrlOrNull()?.pathSegments?.lastOrNull()?.equals("index.pb", ignoreCase = true) == true

    private fun isOutdatedExtension(ext: ExtensionDto): Boolean =
        ext.packageName == "eu.kanade.tachiyomi.extension.all.outdated" ||
            ext.packageName.endsWith(".outdated") ||
            ext.name.startsWith("Outdated App", ignoreCase = true) ||
            ext.name.contains("Update to Mihon", ignoreCase = true)

    private fun ExtensionDto.toAvailableExtension(repo: ExternalExtensionRepo, indexUrl: String): RepoAvailableExtension? {
        val libVersion = extensionLib.substringBeforeLast('.', extensionLib).toDoubleOrNull()
            ?: extensionLib.toDoubleOrNull()
            ?: return null
        val supported = when (repo.type) {
            ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
            ExternalExtensionType.ANIYOMI -> libVersion in 1.2..2.5
            ExternalExtensionType.IREADER, ExternalExtensionType.JAR -> true
        }
        val displayName = when (repo.type) {
            ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
            ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
            ExternalExtensionType.IREADER -> name.removePrefix("IReader: ")
            ExternalExtensionType.JAR -> name
        }
        val languages = sources.map { it.language }.filter { it.isNotEmpty() }.toSet()
        val apkUrl = applyMirror(resolveUrl(indexUrl, resources.apkUrl))
        val iconUrl = when {
            resources.iconUrl.isNotBlank() -> applyMirror(resolveUrl(indexUrl, resources.iconUrl))
            else -> resolveUrl(indexUrl, "icon/${apkUrl.substringAfterLast('/').substringBefore('?').replace(".apk", ".png")}")
        }
        return RepoAvailableExtension(
            type = repo.type,
            name = displayName,
            pkgName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = languages.singleOrNull() ?: "all",
            isNsfw = contentWarning >= 2,
            sourceNames = sources.map { it.name },
            // Installer accepts absolute URLs, so keep the canonical protobuf resource URL intact.
            apkName = apkUrl,
            iconUrl = iconUrl,
            repoUrl = repo.baseUrl,
            repoName = repo.displayName,
            signatureHash = repo.signingKeyFingerprint,
            isCompatible = supported,
        )
    }

    private fun ExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension? {
        if (pkg == "eu.kanade.tachiyomi.extension.all.outdated" || pkg.endsWith(".outdated") ||
            name.startsWith("Outdated App", ignoreCase = true) || name.contains("Update to Mihon", ignoreCase = true)
        ) return null

        val libVersion = version.substringBeforeLast('.', version).toDoubleOrNull()
            ?: if (repo.type == ExternalExtensionType.IREADER) 0.0 else return null
        val supported = when (repo.type) {
            ExternalExtensionType.MIHON -> libVersion in MihonExtensionLoader.LIB_VERSION_MIN..MihonExtensionLoader.LIB_VERSION_MAX
            ExternalExtensionType.ANIYOMI -> libVersion in 1.2..2.5
            ExternalExtensionType.IREADER, ExternalExtensionType.JAR -> true
        }
        val displayName = when (repo.type) {
            ExternalExtensionType.MIHON -> name.removePrefix("Tachiyomi: ")
            ExternalExtensionType.ANIYOMI -> name.removePrefix("Aniyomi: ")
            ExternalExtensionType.IREADER -> name.removePrefix("IReader: ")
            ExternalExtensionType.JAR -> name
        }
        return RepoAvailableExtension(
            type = repo.type,
            name = displayName,
            pkgName = pkg,
            versionName = version,
            versionCode = code,
            libVersion = libVersion,
            lang = lang,
            isNsfw = nsfw == 1,
            sourceNames = sources.orEmpty().map { it.name },
            apkName = apk,
            iconUrl = applyMirror(if (repo.type == ExternalExtensionType.IREADER) "${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}" else "${repo.baseUrl}/icon/$pkg.png"),
            repoUrl = repo.baseUrl,
            repoName = repo.displayName,
            signatureHash = repo.signingKeyFingerprint,
            isCompatible = supported,
        )
    }

    private fun IReaderExtensionIndexDto.toAvailableExtension(repo: ExternalExtensionRepo): RepoAvailableExtension {
        val libVersion = version.substringBeforeLast('.', version).toDoubleOrNull() ?: 0.0
        return RepoAvailableExtension(
            type = repo.type,
            name = name.removePrefix("IReader: "),
            pkgName = pkg,
            versionName = version,
            versionCode = code,
            libVersion = libVersion,
            lang = lang,
            isNsfw = nsfw,
            sourceNames = emptyList(),
            apkName = apk,
            iconUrl = applyMirror("${repo.baseUrl}/icon/${apk.replace(".apk", ".png")}"),
            repoUrl = repo.baseUrl,
            repoName = repo.displayName,
            signatureHash = "",
            isCompatible = true,
        )
    }

    @Keep
    @Serializable
    private data class RepoMetaWrapperDto(
        @SerialName("index_v2") val indexV2: String? = null,
        val meta: RepoMetaDto,
    )

    @Keep
    @Serializable
    private data class RepoMetaDto(
        val name: String,
        @SerialName("shortName") val shortName: String? = null,
        val website: String,
        @SerialName("signingKeyFingerprint") val signingKeyFingerprint: String,
    )

    @Keep
    @Serializable
    private data class ExtensionIndexDto(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String = "all",
        val code: Long,
        val version: String,
        val nsfw: Int = 0,
        val sources: List<ExtensionSourceDto>? = null,
    )

    @Keep
    @Serializable
    private data class ExtensionSourceDto(val name: String)

    @Keep
    @Serializable
    private data class IReaderExtensionIndexDto(
        val name: String = "",
        val pkg: String = "",
        val apk: String = "",
        val lang: String = "en",
        val code: Long = 1,
        val version: String = "1.0",
        val nsfw: Boolean = false,
    )

    @Keep
    @Serializable
    private data class NetworkExtensionStoreDto(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val badgeLabel: String = "",
        @ProtoNumber(3) val signingKey: String = "",
        @ProtoNumber(4) val contact: ContactDto? = null,
        @ProtoNumber(101) val extensionList: ExtensionListDto? = null,
        @ProtoNumber(102) val extensionListUrl: String? = null,
    )

    @Keep
    @Serializable
    private data class ContactDto(
        @ProtoNumber(1) val website: String = "",
        @ProtoNumber(2) val discord: String? = null,
    )

    @Keep
    @Serializable
    private data class ExtensionListDto(
        @ProtoNumber(1) val extensions: List<ExtensionDto> = emptyList(),
    )

    @Keep
    @Serializable
    private data class ExtensionDto(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val packageName: String = "",
        @ProtoNumber(3) val resources: ResourcesDto = ResourcesDto(),
        @ProtoNumber(4) val extensionLib: String = "",
        @ProtoNumber(5) val versionCode: Long = 0L,
        @ProtoNumber(6) val versionName: String = "",
        @ProtoNumber(7) val contentWarning: Int = 0,
        @ProtoNumber(8) val sources: List<SourceDto> = emptyList(),
    )

    @Keep
    @Serializable
    private data class ResourcesDto(
        @ProtoNumber(1) val apkUrl: String = "",
        @ProtoNumber(2) val iconUrl: String = "",
    )

    @Keep
    @Serializable
    private data class SourceDto(
        @ProtoNumber(1) val id: Long = 0L,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val language: String = "",
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(7) val message: String? = null,
    )

    private companion object {
        const val TAG = "ExtensionRepo"
        const val REPO_DETAILS_TIMEOUT_MS = 15_000L
        const val CATALOG_TIMEOUT_MS = 20_000L
    }
}
