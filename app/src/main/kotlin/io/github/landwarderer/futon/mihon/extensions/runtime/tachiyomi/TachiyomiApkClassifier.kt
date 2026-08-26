// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi

import android.content.pm.PackageInfo
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionLoaderSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionMetadataSupport

sealed interface TachiyomiApkClassification {
    data object Extension : TachiyomiApkClassification
    data object NotAnExtension : TachiyomiApkClassification
    data object Ambiguous : TachiyomiApkClassification
}

object TachiyomiApkClassifier {

    fun looksLikeTachiyomiPackage(packageName: String): Boolean {
        return ExternalExtensionLoaderSupport.looksLikeMihonPackage(packageName)
    }

    fun classify(
        pkgInfo: PackageInfo,
        spec: TachiyomiApkEcosystemSpec,
    ): TachiyomiApkClassification {
        val declaresOwnFeature = pkgInfo.reqFeatures?.any { it.name == spec.requiredFeature } == true

        if (spec.strictIdentification) {
            return if (declaresOwnFeature) {
                TachiyomiApkClassification.Extension
            } else {
                TachiyomiApkClassification.NotAnExtension
            }
        }

        val hasPackageName = looksLikeTachiyomiPackage(pkgInfo.packageName)
        val hasMetaData = ExternalExtensionMetadataSupport.hasDeclaredSource(
            metaData = pkgInfo.applicationInfo?.metaData,
            sourceClassKey = spec.sourceMetadataKey,
            sourceFactoryKey = spec.factoryMetadataKey.orEmpty(),
        )
        return if (declaresOwnFeature || (hasPackageName && hasMetaData)) {
            TachiyomiApkClassification.Extension
        } else {
            TachiyomiApkClassification.NotAnExtension
        }
    }
}
