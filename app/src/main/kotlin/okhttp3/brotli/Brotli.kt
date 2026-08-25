package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source
import okio.source
import org.brotli.dec.BrotliInputStream

/**
 * OkHttp 5.5-compatible Brotli algorithm exposed by the Futon host.
 *
 * Keiyoushi extensions compile against Mihon's host-provided OkHttp compression APIs. Keeping
 * this adapter in the app makes it implement the exact CompressionInterceptor interface that
 * Futon's Android OkHttp variant loads.
 */
object Brotli : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding: String
        get() = "br"

    override fun decompress(compressedSource: BufferedSource): Source =
        BrotliInputStream(compressedSource.inputStream()).source()
}
