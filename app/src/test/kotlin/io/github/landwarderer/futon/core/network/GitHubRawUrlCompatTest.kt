package io.github.landwarderer.futon.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GitHubRawUrlCompatTest {

    @Test
    fun legacyRawGithubHostIsCanonicalizedWithoutChangingPath() {
        val input = "https://raw.github.com/keiyoushi/extensions/repo/repo.json?x=1".toHttpUrl()

        val result = canonicalizeLegacyGitHubRawUrl(input)

        assertEquals("raw.githubusercontent.com", result.host)
        assertEquals("/keiyoushi/extensions/repo/repo.json", result.encodedPath)
        assertEquals("x=1", result.encodedQuery)
    }

    @Test
    fun nativeRawGithubusercontentUrlIsLeftUntouched() {
        val input = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb".toHttpUrl()

        assertSame(input, canonicalizeLegacyGitHubRawUrl(input))
    }

    @Test
    fun requestCanonicalizationPreservesMethodAndHeaders() {
        val request = Request.Builder()
            .url("https://raw.github.com/keiyoushi/extensions/repo/index.min.json")
            .header("X-Test", "value")
            .build()

        val result = canonicalizeLegacyGitHubRawRequest(request)

        assertEquals("GET", result.method)
        assertEquals("value", result.header("X-Test"))
        assertEquals("raw.githubusercontent.com", result.url.host)
    }
}
