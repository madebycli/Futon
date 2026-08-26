// Native image response behavior adapted from Kototoro at c1128b91140053b081cc7453c87a16f52ab2f12a.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.core.exceptions.CloudFlareException
import io.github.landwarderer.futon.core.exceptions.InteractiveActionRequiredException
import io.github.landwarderer.futon.mihon.compat.MihonRequestContext
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.MangaPage

/**
 * Let Mihon HttpSource execute its own image request instead of reducing it to a host-built GET.
 * This preserves extension token refresh, retries, request mutation and custom interceptors.
 */
suspend fun MihonMangaRepository.fetchNativePageResponse(
    pageUrl: String,
    page: MangaPage,
): Response? {
    val httpSource = mihonSource as? HttpSource ?: return null
    val mihonPage = page.toNativeMihonPage(pageUrl)

    return rethrowMihonImageExceptions {
        MihonRequestContext.withSource(source) {
            httpSource.getImage(mihonPage)
        }
    }
}

private fun MangaPage.toNativeMihonPage(imageUrl: String): Page {
    var originalPageUrl = url
    var originalImageUrl = imageUrl

    if (url.startsWith("mihon://")) {
        val uri = android.net.Uri.parse(url)
        uri.getQueryParameter("page_url")?.takeIf(String::isNotBlank)?.let {
            originalPageUrl = it
        }
        if (url.startsWith("mihon://image")) {
            uri.getQueryParameter("image_url")?.takeIf(String::isNotBlank)?.let {
                originalImageUrl = it
            }
        }
    }

    return Page(
        index = id.toInt(),
        url = originalPageUrl,
        imageUrl = originalImageUrl,
    )
}

private suspend inline fun <T> rethrowMihonImageExceptions(crossinline block: suspend () -> T): T {
    try {
        return block()
    } catch (e: RuntimeException) {
        when (val cause = e.cause) {
            is CloudFlareException -> throw cause
            is InteractiveActionRequiredException -> throw cause
            is java.io.IOException -> throw cause
            else -> throw e
        }
    }
}
