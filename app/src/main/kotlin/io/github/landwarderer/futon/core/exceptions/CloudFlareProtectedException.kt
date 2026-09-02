// Request-context fields adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.exceptions

import io.github.landwarderer.futon.core.model.UnknownMangaSource
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper

class CloudFlareProtectedException(
    override val url: String,
    source: MangaSource?,
    @Transient val headers: Headers,
    val method: String = "GET",
    val body: String? = null,
    val contentType: String? = headers["Content-Type"],
    val originalUrl: String = url,
) : CloudFlareException("Protected by CloudFlare", CloudFlareHelper.PROTECTION_CAPTCHA) {

    override val source: MangaSource = source ?: UnknownMangaSource
}
