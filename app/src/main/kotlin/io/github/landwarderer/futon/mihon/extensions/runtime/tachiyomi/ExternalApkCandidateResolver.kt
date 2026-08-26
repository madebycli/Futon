// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi

import android.content.pm.PackageInfo

enum class ExternalApkCandidateSelection {
    SYSTEM_FIRST_KEEP_FIRST,
    VERSION_HIGHER_FIRST_TIE_SYSTEM,
}

object ExternalApkCandidateResolver {

    fun resolve(
        installed: List<PackageInfo>,
        local: List<PackageInfo>,
        mode: ExternalApkCandidateSelection,
    ): List<PackageInfo> = when (mode) {
        ExternalApkCandidateSelection.SYSTEM_FIRST_KEEP_FIRST -> {
            (installed + local).distinctBy { it.packageName }
        }

        ExternalApkCandidateSelection.VERSION_HIGHER_FIRST_TIE_SYSTEM -> {
            @Suppress("DEPRECATION")
            val select = fun(candidates: List<Candidate>): PackageInfo {
                return candidates.maxWith(
                    compareBy<Candidate> { it.pkgInfo.versionCode }
                        .thenBy { it.fromSystem },
                ).pkgInfo
            }
            (installed.map { Candidate(it, fromSystem = true) } +
                local.map { Candidate(it, fromSystem = false) })
                .groupBy { it.pkgInfo.packageName }
                .map { (_, candidates) -> select(candidates) }
        }
    }

    private data class Candidate(
        val pkgInfo: PackageInfo,
        val fromSystem: Boolean,
    )
}
