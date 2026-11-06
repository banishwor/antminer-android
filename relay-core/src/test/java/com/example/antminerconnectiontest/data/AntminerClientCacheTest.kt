package com.example.antminerconnectiontest.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.1.2 stale digest-auth cache eviction: a final 401 must be able
 * to drop the credential set's cached client so the next attempt
 * builds a fresh one and runs a clean digest challenge (a miner
 * reboot changes its nonce; the library's auth cache never expires).
 * Client construction is network-free, so the cache behaviour is
 * directly testable on the JVM.
 */
class AntminerClientCacheTest {

    @Test
    fun `clientFor caches one client per credential set`() {
        val client = AntminerClient()
        val first = client.clientFor("192.0.2.10", "root", "secret")
        assertSame(first, client.clientFor("192.0.2.10", "root", "secret"))
        // A different password is a different credential set.
        assertNotSame(first, client.clientFor("192.0.2.10", "root", "other"))
    }

    @Test
    fun `invalidateClient evicts only that credential set`() {
        val client = AntminerClient()
        val evicted = client.clientFor("192.0.2.11", "root", "secret")
        val untouched = client.clientFor("192.0.2.12", "root", "secret")

        assertTrue(AntminerClient.invalidateClient("192.0.2.11", "root", "secret"))

        // The evicted set is rebuilt fresh on next use…
        assertNotSame(evicted, client.clientFor("192.0.2.11", "root", "secret"))
        // …while other credential sets keep their cached client.
        assertSame(untouched, client.clientFor("192.0.2.12", "root", "secret"))
    }

    @Test
    fun `invalidateClient reports false when nothing was cached`() {
        assertFalse(AntminerClient.invalidateClient("192.0.2.99", "root", "secret"))
    }

    @Test
    fun `invalidateClient respects the timeout profile in the key`() {
        val client = AntminerClient()
        client.clientFor("192.0.2.13", "root", "secret", readTimeoutSeconds = 120)
        // The default-timeout key is a different entry: nothing evicted.
        assertFalse(AntminerClient.invalidateClient("192.0.2.13", "root", "secret"))
        assertTrue(
            AntminerClient.invalidateClient("192.0.2.13", "root", "secret", readTimeoutSeconds = 120)
        )
    }
}
