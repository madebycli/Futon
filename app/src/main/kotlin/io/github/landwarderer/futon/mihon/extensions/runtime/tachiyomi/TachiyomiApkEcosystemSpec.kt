// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi

/**
 * Static declaration of a Tachiyomi-ABI extension ecosystem.
 * Futon currently consumes the Mihon/Keiyoushi manga ecosystem, so unrelated Kototoro
 * ecosystems are intentionally not pulled into this fork.
 */
data class TachiyomiApkEcosystemSpec(
    val ecosystemDir: String,
    val sourcePrefix: String,
    val requiredFeature: String,
    val sourceMetadataKey: String,
    val factoryMetadataKey: String?,
    val nsfwMetadataKey: String,
    val languageMarker: String,
    val acceptedLibVersions: Set<String>,
    val strictIdentification: Boolean,
)

object TachiyomiApkEcosystemSpecs {
    val MIHON = TachiyomiApkEcosystemSpec(
        ecosystemDir = "mihon",
        sourcePrefix = "MIHON_",
        requiredFeature = "tachiyomi.extension",
        sourceMetadataKey = "tachiyomi.extension.class",
        factoryMetadataKey = "tachiyomi.extension.factory",
        nsfwMetadataKey = "tachiyomi.extension.nsfw",
        languageMarker = "extension",
        acceptedLibVersions = (12..19).map { it / 10.0 }.map(Double::toString).toSet(),
        strictIdentification = false,
    )
}
