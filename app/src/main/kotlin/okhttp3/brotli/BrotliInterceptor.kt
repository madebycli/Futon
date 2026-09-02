package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okhttp3.Gzip

/**
 * OkHttp 5.5-compatible Brotli interceptor exposed by the Futon host.
 */
object BrotliInterceptor : CompressionInterceptor(Brotli, Gzip)
