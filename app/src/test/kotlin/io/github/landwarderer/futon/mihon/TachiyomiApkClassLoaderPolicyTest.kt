// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon

import io.github.landwarderer.futon.mihon.extensions.runtime.tachiyomi.TachiyomiApkClassLoaderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TachiyomiApkClassLoaderPolicyTest {

    @Test
    fun platformAndStdlibClassesAlwaysDelegateToParent() {
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("java.lang.String"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("kotlinx.coroutines.BuildersKt"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("android.content.Context"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("androidx.core.content.pm.PackageInfoCompat"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("okhttp3.OkHttpClient"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("uy.kohesive.injekt.api.Injekt"))
    }

    @Test
    fun hostOwnedMangaAbiDelegatesToParent() {
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.Source"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.online.HttpSource"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.model.Page"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.network.NetworkHelper"))
    }

    @Test
    fun extensionImplementationClassesLoadChildFirst() {
        assertFalse(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.extension.en.example.ExampleSource"))
        assertFalse(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("com.example.ExtensionRuntime"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("org.jsoup.nodes.Document"))
    }

    @Test
    fun defaultMethodBridgeClassesLoadChildFirstWhileInterfacesStayParentBound() {
        assertFalse(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.Source$-CC"))
        assertFalse(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.CatalogueSource$-CC"))
        assertFalse(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.Source\$DefaultImpls"))
        assertFalse(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.CatalogueSource\$DefaultImpls"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.Source"))
        assertTrue(TachiyomiApkClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.CatalogueSource"))
    }

    @Test
    fun legacyAliasDelegatesThroughSharedPolicy() {
        assertTrue(ChildFirstClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.source.Source"))
        assertFalse(ChildFirstClassLoaderPolicy.shouldDelegateToParent("eu.kanade.tachiyomi.extension.en.example.ExampleSource"))
    }
}
