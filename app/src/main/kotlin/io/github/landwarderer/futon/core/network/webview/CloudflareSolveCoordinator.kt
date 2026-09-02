// Ported and adapted from Kototoro at f4f37a5b7290da05c10b9325912f2a37ebeff0f9.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.network.webview

import io.github.landwarderer.futon.core.network.CloudflareHostCooldown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Shares one off-screen Cloudflare solve between concurrent callers for the same host. */
class CloudflareSolveCoordinator(
    private val hostCooldown: CloudflareHostCooldown,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gates = ConcurrentHashMap<String, HostGate>()

    suspend fun solve(host: String, action: suspend () -> Boolean): Boolean {
        val normalizedHost = host.lowercase()
        if (hostCooldown.isInCooldown(normalizedHost)) return false
        return gates.computeIfAbsent(normalizedHost) { HostGate(normalizedHost) }.join(action)
    }

    private inner class HostGate(private val host: String) {
        private val mutex = Mutex()
        private var active: Deferred<Boolean>? = null
        private var waiters = 0

        suspend fun join(action: suspend () -> Boolean): Boolean {
            val deferred = mutex.withLock {
                waiters++
                active?.takeIf { !it.isCompleted }
                    ?: scope.async { runSolve(action) }.also { active = it }
            }
            return try {
                deferred.await()
            } finally {
                mutex.withLock {
                    waiters--
                    if (waiters <= 0) {
                        waiters = 0
                        if (active === deferred) {
                            active = null
                            if (deferred.isActive) deferred.cancel()
                        }
                        if (active == null) gates.remove(host, this@HostGate)
                    }
                }
            }
        }

        private suspend fun runSolve(action: suspend () -> Boolean): Boolean {
            val result = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!result) hostCooldown.coolDown(host)
            return result
        }
    }
}
