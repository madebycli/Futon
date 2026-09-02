// Adapted from Kototoro Mihon model at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.model

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source

/**
 * Result of loading a Mihon extension.
 */
sealed class MihonLoadResult {

    data class Success(
        val pkgName: String,
        val appName: String,
        val versionCode: Long,
        val versionName: String,
        val libVersion: Double,
        val lang: String,
        val isNsfw: Boolean,
        val sources: List<Source>,
        val isManagedLocal: Boolean = false,
    ) : MihonLoadResult() {
        val catalogueSources: List<CatalogueSource>
            get() = sources.filterIsInstance<CatalogueSource>()
    }

    data class Error(
        val pkgName: String,
        val message: String,
        val exception: Throwable? = null,
    ) : MihonLoadResult()

    data class Untrusted(
        val pkgName: String,
        val appName: String,
        val versionCode: Long,
        val versionName: String,
    ) : MihonLoadResult()
}

data class MihonExtensionInfo(
    val pkgName: String,
    val appName: String,
    val versionCode: Long,
    val versionName: String,
    val libVersion: Double,
    val lang: String,
    val isNsfw: Boolean,
    val sourceClassName: String,
    val apkPath: String,
)
