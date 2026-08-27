package io.github.landwarderer.futon.core.network.webview

import io.github.landwarderer.futon.core.network.CloudflareHostCooldown
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CloudflareSolveCoordinatorTest {

    @Test
    fun `concurrent callers for same host share one solve`() = runBlocking {
        val cooldown = CloudflareHostCooldown().apply { cooldownMillis = 0L }
        val coordinator = CloudflareSolveCoordinator(cooldown)
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async(Dispatchers.Default) {
            coordinator.solve("COMIX.TO") {
                calls.incrementAndGet()
                started.complete(Unit)
                release.await()
                true
            }
        }
        started.await()

        val followers = List(8) {
            async(Dispatchers.Default) {
                coordinator.solve("comix.to") {
                    calls.incrementAndGet()
                    true
                }
            }
        }
        release.complete(Unit)

        assertTrue(first.await())
        assertTrue(followers.awaitAll().all { it })
        assertEquals(1, calls.get())
    }

    @Test
    fun `failed solve cools host down until window expires`() = runBlocking {
        val now = AtomicLong(1_000L)
        val cooldown = CloudflareHostCooldown().apply {
            cooldownMillis = 500L
            nowMillis = { now.get() }
        }
        val coordinator = CloudflareSolveCoordinator(cooldown)
        val calls = AtomicInteger(0)

        assertFalse(
            coordinator.solve("comix.to") {
                calls.incrementAndGet()
                false
            },
        )
        assertFalse(
            coordinator.solve("COMIX.TO") {
                calls.incrementAndGet()
                true
            },
        )
        assertEquals(1, calls.get())

        now.addAndGet(501L)
        assertTrue(
            coordinator.solve("comix.to") {
                calls.incrementAndGet()
                true
            },
        )
        assertEquals(2, calls.get())
    }
}
