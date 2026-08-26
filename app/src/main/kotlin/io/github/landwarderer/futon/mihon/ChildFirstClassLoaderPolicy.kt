// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon

import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.TachiyomiApkClassLoaderPolicy

/**
 * Legacy alias for the shared [TachiyomiApkClassLoaderPolicy]. Kept beside the original
 * Futon loader so the Mihon runtime and tests share one Tachiyomi ABI ownership policy.
 */
@Deprecated("Use TachiyomiApkClassLoaderPolicy from extensions/runtime/tachiyomi instead")
internal object ChildFirstClassLoaderPolicy {

    fun shouldDelegateToParent(className: String): Boolean =
        TachiyomiApkClassLoaderPolicy.shouldDelegateToParent(className)
}
