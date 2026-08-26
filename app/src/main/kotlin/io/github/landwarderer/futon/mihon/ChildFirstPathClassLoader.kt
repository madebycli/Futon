// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon

import dalvik.system.DexClassLoader
import java.io.File

/**
 * A ClassLoader that loads classes from its own path before delegating to its parent.
 *
 * Mihon extensions may bundle different library versions than Futon. Host-owned runtime and
 * Tachiyomi ABI namespaces are explicitly delegated to the parent through
 * [ChildFirstClassLoaderPolicy], while extension implementation and generated bridge classes
 * remain child-first.
 */
class ChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : DexClassLoader(
    dexPath,
    File(dexPath).parentFile?.absolutePath,
    librarySearchPath,
    parent,
) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (ChildFirstClassLoaderPolicy.shouldDelegateToParent(name)) {
            return parent.loadClass(name)
        }

        return try {
            findLoadedClass(name) ?: findClass(name)
        } catch (_: ClassNotFoundException) {
            parent.loadClass(name)
        }
    }
}
