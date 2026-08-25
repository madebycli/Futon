package io.github.landwarderer.futon.mihon.compat

import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class MihonNetworkHelperTest {

    @Test
    fun defaultClientExposesKeiyoushiRequiredInterceptors() {
        val helper = MihonNetworkHelper(
            baseClient = OkHttpClient.Builder().build(),
            cookieJar = CookieJar.NO_COOKIES,
        )

        val names = helper.client.interceptors.map { it.javaClass.simpleName }

        assertEquals("UncaughtExceptionInterceptor", names[0])
        assertEquals("UserAgentInterceptor", names[1])
        assertEquals("CloudflareInterceptor", names[2])
        assertTrue(names.containsAll(REQUIRED_INTERCEPTORS))
    }

    @Test
    fun derivedClientRetainsKeiyoushiRequiredInterceptors() {
        val helper = MihonNetworkHelper(
            baseClient = OkHttpClient.Builder().build(),
            cookieJar = CookieJar.NO_COOKIES,
        )

        val names = helper.client.newBuilder().build().interceptors
            .map { it.javaClass.simpleName }

        assertTrue(names.containsAll(REQUIRED_INTERCEPTORS))
    }

    @Test
    fun userAgentInterceptorAddsDefaultOnlyWhenMissing() {
        val missingUserAgent = executeThroughUserAgentInterceptor(
            request = Request.Builder().url("https://example.com/").build(),
        )
        assertEquals(TEST_USER_AGENT, missingUserAgent.header("User-Agent"))

        val customRequest = Request.Builder()
            .url("https://example.com/")
            .header("User-Agent", "Source-Specific-UA")
            .build()
        val preservedUserAgent = executeThroughUserAgentInterceptor(customRequest)
        assertEquals("Source-Specific-UA", preservedUserAgent.header("User-Agent"))
    }

    @Test
    fun uncaughtExceptionInterceptorWrapsUncheckedExceptionsAsIoException() {
        val original = IllegalStateException("extension failure")
        val client = OkHttpClient.Builder()
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor { throw original }
            .build()

        try {
            client.newCall(Request.Builder().url("https://example.com/").build()).execute()
            fail("Expected IOException")
        } catch (e: IOException) {
            assertSame(original, e.cause)
        }
    }

    private fun executeThroughUserAgentInterceptor(request: Request): Request {
        val capturedRequest = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor { TEST_USER_AGENT })
            .addInterceptor { chain ->
                capturedRequest.set(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }
        return capturedRequest.get()
    }

    companion object {
        private const val TEST_USER_AGENT = "Futon-Mihon-Test-UA"
        private val REQUIRED_INTERCEPTORS = setOf(
            "UncaughtExceptionInterceptor",
            "UserAgentInterceptor",
            "CloudflareInterceptor",
        )
    }
}
