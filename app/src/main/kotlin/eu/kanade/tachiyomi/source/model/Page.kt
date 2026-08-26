// Ported and adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Mihon-compatible Page with the complete parent-owned compatibility surface. */
@Serializable
open class Page @JvmOverloads constructor(
    var index: Int,
    var url: String = "",
    var imageUrl: String? = null,
    @Transient var uri: Uri? = null,
) : ProgressListener {

    val number: Int
        get() = index + 1

    /** Additive text payload used by Tachiyomi-ABI forks. Manga sources may leave it null. */
    @Transient
    var text: String? = null

    @Transient
    private val _statusFlow = MutableStateFlow<State>(State.Queue)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()

    @Transient
    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()

    @Transient
    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    fun copy(
        index: Int = this.index,
        url: String = this.url,
        imageUrl: String? = this.imageUrl,
    ): Page = Page(index, url, imageUrl)

    sealed interface State {
        data object Queue : State
        data object LoadPage : State
        data object DownloadImage : State
        data object Ready : State
        data class Error(val error: Throwable) : State
    }
}
