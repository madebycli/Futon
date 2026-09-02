package okhttp3.zstd

import com.squareup.zstd.okio.zstdDecompress
import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source

/**
 * OkHttp 5.5-compatible Zstandard algorithm exposed by the Futon host.
 */
object Zstd : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding: String
        get() = "zstd"

    override fun decompress(compressedSource: BufferedSource): Source =
        compressedSource.zstdDecompress()
}
