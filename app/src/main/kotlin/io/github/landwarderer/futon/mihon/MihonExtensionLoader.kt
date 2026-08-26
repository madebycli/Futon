// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon

import android.content.Context
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import io.github.landwarderer.futon.mihon.compat.MihonInjektBridge
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionLoaderSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionMetadataSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.ExternalExtensionSourceLoaderSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.LocalApkExtensionSupport
import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.ExternalApkCandidateResolver
import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.ExternalApkCandidateSelection
import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.TachiyomiApkClassification
import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.TachiyomiApkClassifier
import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.TachiyomiApkEcosystemSpecs
import io.github.landwarderer.futon.mihon.model.MihonExtensionInfo
import io.github.landwarderer.futon.mihon.model.MihonLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loader for Mihon/Keiyoushi extension APKs.
 *
 * Supports both Android-installed packages and app-private managed APK archives. System packages
 * keep precedence for duplicate package names, matching Kototoro's Mihon semantics.
 */
@Singleton
class MihonExtensionLoader @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val injektBridge: dagger.Lazy<MihonInjektBridge>,
) {
    companion object {
        private const val TAG = "MihonExtensionLoader"
        private const val ECOSYSTEM_DIR = "mihon"
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        private const val METADATA_NEW_NAME = "tachiyomix.name"

        const val LIB_VERSION_MIN = 1.2
        const val LIB_VERSION_MAX = 1.9
    }

    suspend fun loadExtensions(context: Context): List<MihonLoadResult> = withContext(Dispatchers.IO) {
        try {
            injektBridge.get().initialize()
            val pkgManager = context.packageManager
            val installedPkgs = ExternalExtensionLoaderSupport.getInstalledPackages(pkgManager)
            val localPkgs = LocalApkExtensionSupport.getLocalArchivePackages(context, pkgManager, ECOSYSTEM_DIR)

            val extPkgs = ExternalApkCandidateResolver.resolve(
                installed = installedPkgs.filter { isPackageAnExtension(it) },
                local = localPkgs.filter { isPackageAnExtension(it) },
                mode = ExternalApkCandidateSelection.SYSTEM_FIRST_KEEP_FIRST,
            )

            if (extPkgs.isEmpty()) {
                android.util.Log.d(TAG, "No Mihon extensions found")
                return@withContext emptyList()
            }

            android.util.Log.i(TAG, "Found ${extPkgs.size} Mihon extension(s) to load")
            extPkgs.map { pkgInfo: PackageInfo ->
                async {
                    try {
                        loadExtension(context, pkgInfo)
                    } catch (e: Throwable) {
                        android.util.Log.e(TAG, "Failed to load extension ${pkgInfo.packageName}", e)
                        MihonLoadResult.Error(pkgInfo.packageName, "Exception: ${e.message}", e)
                    }
                }
            }.awaitAll()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to load extensions", e)
            emptyList()
        }
    }

    suspend fun loadExtension(context: Context, packageName: String): MihonLoadResult? = withContext(Dispatchers.IO) {
        injektBridge.get().initialize()
        val pkgManager = context.packageManager
        val pkgInfo = ExternalExtensionLoaderSupport.getPackageInfoOrNull(pkgManager, packageName)
            ?: LocalApkExtensionSupport.getLocalArchivePackageInfoOrNull(
                context,
                pkgManager,
                ECOSYSTEM_DIR,
                packageName,
            )
            ?: return@withContext null

        if (!isPackageAnExtension(pkgInfo)) return@withContext null
        loadExtension(context, pkgInfo)
    }

    fun getInstalledExtensions(context: Context): List<MihonExtensionInfo> {
        val pkgManager = context.packageManager
        val installedPkgs = ExternalExtensionLoaderSupport.getInstalledPackages(pkgManager)
        val localPkgs = LocalApkExtensionSupport.getLocalArchivePackages(context, pkgManager, ECOSYSTEM_DIR)

        return ExternalApkCandidateResolver.resolve(
            installed = installedPkgs.filter { isPackageAnExtension(it) },
            local = localPkgs.filter { isPackageAnExtension(it) },
            mode = ExternalApkCandidateSelection.SYSTEM_FIRST_KEEP_FIRST,
        ).mapNotNull { extractExtensionInfo(it) }
    }

    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean {
        return TachiyomiApkClassifier.classify(
            pkgInfo = pkgInfo,
            spec = TachiyomiApkEcosystemSpecs.MIHON,
        ) == TachiyomiApkClassification.Extension
    }

    private fun extractExtensionInfo(pkgInfo: PackageInfo): MihonExtensionInfo? {
        val completePkgInfo = refreshIfInstalled(pkgInfo)
        val pkgName = completePkgInfo.packageName
        val appInfo = completePkgInfo.applicationInfo ?: run {
            android.util.Log.w(TAG, "extractExtensionInfo($pkgName): applicationInfo is null")
            return null
        }
        val metaData = ExternalExtensionMetadataSupport.getMetaDataOrNull(appInfo) ?: run {
            android.util.Log.w(TAG, "extractExtensionInfo($pkgName): metaData is null")
            return null
        }
        val versionName = completePkgInfo.versionName ?: return null
        val declaredSource = ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
            metaData = metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
            nsfwKey = METADATA_NSFW,
        ) ?: return null

        val libVersion = declaredSource.libVersionOverride ?: try {
            versionName.split('.').let { parts ->
                if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble() else parts[0].toDouble()
            }
        } catch (_: Exception) {
            1.4
        }

        val appName = metaData.getString(METADATA_NEW_NAME) ?: try {
            ExternalExtensionLoaderSupport.getAppLabel(applicationContext, appInfo)
        } catch (_: Exception) {
            null
        } ?: pkgName.substringAfterLast('.')

        return MihonExtensionInfo(
            pkgName = pkgName,
            appName = appName,
            versionCode = PackageInfoCompat.getLongVersionCode(completePkgInfo),
            versionName = versionName,
            libVersion = libVersion,
            lang = ExternalExtensionLoaderSupport.extractLanguage(pkgName, "extension"),
            isNsfw = declaredSource.isNsfw,
            sourceClassName = declaredSource.sourceClassName,
            apkPath = appInfo.sourceDir ?: return null,
        )
    }

    private fun loadExtension(context: Context, pkgInfo: PackageInfo): MihonLoadResult {
        val completePkgInfo = refreshIfInstalled(pkgInfo, context)
        val pkgName = completePkgInfo.packageName
        val appInfo = completePkgInfo.applicationInfo
            ?: return MihonLoadResult.Error(pkgName, "No ApplicationInfo")
        val versionName = completePkgInfo.versionName
            ?: return MihonLoadResult.Error(pkgName, "No version name")
        val versionCode = PackageInfoCompat.getLongVersionCode(completePkgInfo)
        val metaData = ExternalExtensionMetadataSupport.getMetaDataOrNull(appInfo)
            ?: return MihonLoadResult.Error(pkgName, "No meta-data in manifest")

        val declaredSource = ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
            metaData = metaData,
            sourceClassKey = METADATA_SOURCE_CLASS,
            sourceFactoryKey = METADATA_SOURCE_FACTORY,
            nsfwKey = METADATA_NSFW,
        ) ?: return MihonLoadResult.Error(pkgName, "No source class specified in manifest")

        val libVersion = declaredSource.libVersionOverride
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
            ?: try {
                versionName.split('.').let { parts ->
                    if (parts.size >= 2) "${parts[0]}.${parts[1]}".toDouble() else parts[0].toDouble()
                }
            } catch (_: Exception) {
                return MihonLoadResult.Error(pkgName, "Invalid lib version format: $versionName")
            }

        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            return MihonLoadResult.Error(
                pkgName,
                "Incompatible lib version: $libVersion (supported: $LIB_VERSION_MIN-$LIB_VERSION_MAX) for versionName=$versionName",
            )
        }

        val appName = metaData.getString(METADATA_NEW_NAME)
            ?: ExternalExtensionLoaderSupport.getAppLabel(context, appInfo)
        val lang = ExternalExtensionLoaderSupport.extractLanguage(pkgName, "extension")

        val classLoader = try {
            val dexPath = LocalApkExtensionSupport.prepareLoadableApkPath(
                context = context,
                ecosystem = ECOSYSTEM_DIR,
                pkgName = pkgName,
                sourcePath = appInfo.sourceDir,
            )
            ChildFirstPathClassLoader(
                dexPath,
                appInfo.nativeLibraryDir,
                context.classLoader,
            )
        } catch (e: Throwable) {
            return MihonLoadResult.Error(pkgName, "Failed to create ClassLoader", e)
        }

        val sources = try {
            loadSources(pkgName, declaredSource.sourceClassName, classLoader)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to load sources from $pkgName", e)
            return MihonLoadResult.Error(pkgName, "Failed to load sources: ${e.message}", e)
        }

        if (sources.isEmpty()) {
            return MihonLoadResult.Error(pkgName, "No sources loaded from extension")
        }

        android.util.Log.i(TAG, "Successfully loaded ${sources.size} source(s) from $pkgName")
        return MihonLoadResult.Success(
            pkgName = pkgName,
            appName = appName,
            versionCode = versionCode,
            versionName = versionName,
            libVersion = libVersion,
            lang = lang,
            isNsfw = declaredSource.isNsfw,
            sources = sources,
            isManagedLocal = LocalApkExtensionSupport.isManagedLocalPackage(
                context,
                ECOSYSTEM_DIR,
                appInfo.sourceDir,
            ),
        )
    }

    private fun refreshIfInstalled(pkgInfo: PackageInfo, context: Context = applicationContext): PackageInfo {
        val sourceDir = pkgInfo.applicationInfo?.sourceDir
        return if (LocalApkExtensionSupport.isManagedLocalPackage(context, ECOSYSTEM_DIR, sourceDir)) {
            pkgInfo
        } else {
            ExternalExtensionLoaderSupport.refreshPackageInfoIfNeeded(context.packageManager, pkgInfo)
        }
    }

    private fun loadSources(
        pkgName: String,
        sourceClassNames: String,
        classLoader: ClassLoader,
    ): List<Source> {
        return ExternalExtensionSourceLoaderSupport.loadSources(
            pkgName = pkgName,
            sourceClassNames = sourceClassNames,
            classLoader = classLoader,
            asSource = { it as? Source },
            createSourcesFromFactory = { (it as? SourceFactory)?.createSources() },
            onUnknownInstance = { className ->
                android.util.Log.w(TAG, "Unknown instance type in $pkgName: $className")
            },
        )
    }
}
