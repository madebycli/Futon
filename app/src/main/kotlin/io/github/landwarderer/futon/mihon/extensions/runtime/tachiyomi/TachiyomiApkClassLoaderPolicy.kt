// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi

/**
 * Shared child-first class-loading policy for Tachiyomi-ABI extensions.
 *
 * Classes whose package starts with one of [parentPackages] are always loaded by the host
 * (parent) classloader: language/stdlib/android/platform runtimes plus the host-owned ABI.
 * Everything else is loaded child-first from the extension APK.
 */
internal object TachiyomiApkClassLoaderPolicy {

    internal val parentPackages = setOf(
        "java.",
        "javax.",
        "kotlin.",
        "kotlinx.coroutines.",
        "android.",
        "androidx.",
        "org.json.",
        "org.jsoup.",
        "okhttp3.",
        "okio.",
        "rx.",
        "eu.kanade.tachiyomi.source.",
        "eu.kanade.tachiyomi.source.model.",
        "eu.kanade.tachiyomi.source.online.",
        "eu.kanade.tachiyomi.network.",
        "eu.kanade.tachiyomi.util.",
        "uy.kohesive.injekt.",
        "ireader.core.",
        "io.ktor.",
        "com.fleeksoft.",
    )

    fun shouldDelegateToParent(className: String): Boolean {
        // Bridge/compat classes are D8 build artifacts that travel inside extension APKs.
        // They have no host-side equivalent, so they must load child-first while the
        // interfaces themselves remain host-owned to preserve one ABI identity.
        if (className.endsWith("$-CC") || className.contains("\$DefaultImpls")) {
            return false
        }
        return parentPackages.any(className::startsWith)
    }
}
