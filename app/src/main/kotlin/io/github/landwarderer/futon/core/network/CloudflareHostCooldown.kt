// Ported from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.network

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Short per-host cooldown after failed automatic Cloudflare solves. */
@Singleton
class CloudflareHostCooldown @Inject constructor() {
    @Volatile
    var cooldownMillis: Long = DEFAULT_COOLDOWN_MS

    @Volatile
    internal var nowMillis: () -> Long = System::currentTimeMillis

    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    fun coolDown(host: String) {
        if (host.isBlank()) return
        val duration = cooldownMillis.coerceAtLeast(0L)
        if (duration == 0L) cooldownUntil.remove(host)
        else cooldownUntil[host.lowercase()] = nowMillis() + duration
    }

    fun isInCooldown(host: String): Boolean {
        if (host.isBlank()) return false
        val key = host.lowercase()
        val until = cooldownUntil[key] ?: return false
        if (nowMillis() >= until) {
            cooldownUntil.remove(key, until)
            return false
        }
        return true
    }

    fun clear() = cooldownUntil.clear()

    companion object {
        const val DEFAULT_COOLDOWN_MS = 30_000L
    }
}
