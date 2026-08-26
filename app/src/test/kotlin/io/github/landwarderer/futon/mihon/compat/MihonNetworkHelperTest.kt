package io.github.landwarderer.futon.mihon.compat

import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import eu.kanade.tachiyomi.source.model.SManga
import io.github.landwarderer.futon.core.network.webview.CaptchaContinuationClient
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun hostRuntimeInitializesOkHttpCompressionClasses() {
        val loader = MihonNetworkHelperTest::class.java.classLoader
        val algorithmClass = Class.forName(
            "okhttp3.CompressionInterceptor\$DecompressionAlgorithm",
            false,
            loader,
        )

        val brotliAlgorithmClass = Class.forName(
            "okhttp3.brotli.Brotli",
            true,
            loader,
        )
        assertTrue(algorithmClass.isAssignableFrom(brotliAlgorithmClass))

        val brotliInterceptorClass = Class.forName(
            "okhttp3.brotli.BrotliInterceptor",
            true,
            loader,
        )
        assertEquals("okhttp3.brotli.BrotliInterceptor", brotliInterceptorClass.name)

        val zstdAlgorithmClass = Class.forName(
            "okhttp3.zstd.Zstd",
            true,
            loader,
        )
        assertTrue(algorithmClass.isAssignableFrom(zstdAlgorithmClass))
    }

    @Test
    fun hostSMangaProvidesTachiyomix16MemoApi() {
        val manga = SManga.create()
        val memo = JsonObject(mapOf("source-key" to JsonPrimitive("value")))

        manga.memo = memo

        assertEquals(memo, manga.memo)
        val setter = SManga::class.java.getMethod("setMemo", JsonObject::class.java)
        val getter = SManga::class.java.getMethod("getMemo")
        assertEquals(Void.TYPE, setter.returnType)
        assertEquals(JsonObject::class.java, getter.returnType)
    }

    @Test
    fun hostSerializationProvidesGeneratedSerializerTypeParametersApi() {
        val generatedSerializer = Class.forName(
            "kotlinx.serialization.internal.GeneratedSerializer",
            false,
            MihonNetworkHelperTest::class.java.classLoader,
        )

        val method = generatedSerializer.methods.firstOrNull {
            it.name == "typeParametersSerializers" && it.parameterCount == 0
        }

        assertNotNull(
            "Current Keiyoushi serializers require GeneratedSerializer.typeParametersSerializers()",
            method,
        )
    }

    @Test
    fun cloudflareWebViewClientDoesNotProxyBrowserRequestsThroughOkHttp() {
        val declaredMethodNames = CaptchaContinuationClient::class.java.declaredMethods
            .map { it.name }
            .toSet()

        assertFalse(
            "Cloudflare WebView must keep Chromium networking like Usagi; do not override shouldInterceptRequest",
            declaredMethodNames.contains("shouldInterceptRequest"),
        )
    }

    @Test
    fun defaultClientDropsKeiyoushiForbiddenNetworkInterceptors() {
        val baseClient = OkHttpClient.Builder()
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor())
            .addNetworkInterceptor(PassthroughNetworkInterceptor())
            .build()
        val helper = MihonNetworkHelper(
            baseClient = baseClient,
            cookieJar = CookieJar.NO_COOKIES,
        )

        val names = helper.client.networkInterceptors.map { it.javaClass.simpleName }

        assertFalse(names.contains("IgnoreGzipInterceptor"))
        assertFalse(names.contains("BrotliInterceptor"))
        assertTrue(names.contains("PassthroughNetworkInterceptor"))
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
    fun sourceContextInterceptorRestoresMissingContentSourceTag() {
        val source = object : ContentSource {
            override val name = "MIHON_TEST"
            override val locale = "en"
            override val contentType = ContentType.MANGA
        }
        val capturedRequest = AtomicReference<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(MihonSourceContextInterceptor())
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

        MihonRequestContext.withSourceBlocking(source) {
            client.newCall(Request.Builder().url("https://example.com/image.jpg").build())
                .execute()
                .close()
        }

        assertSame(source, capturedRequest.get().tag(ContentSource::class.java))
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

    private open class PassthroughNetworkInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }

    private class IgnoreGzipInterceptor : PassthroughNetworkInterceptor()

    private class BrotliInterceptor : PassthroughNetworkInterceptor()

    companion object {
        private const val TEST_USER_AGENT = "Futon-Mihon-Test-UA"
        private val REQUIRED_INTERCEPTORS = setOf(
            "UncaughtExceptionInterceptor",
            "UserAgentInterceptor",
            "CloudflareInterceptor",
        )
    }
}
