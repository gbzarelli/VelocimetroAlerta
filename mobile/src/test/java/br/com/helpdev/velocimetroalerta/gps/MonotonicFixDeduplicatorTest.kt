package br.com.helpdev.velocimetroalerta.gps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotonicFixDeduplicatorTest {
    @Test
    fun acceptsOnlyIncreasingPositiveFixTimes() {
        val deduplicator = MonotonicFixDeduplicator()

        assertFalse(deduplicator.shouldAccept(0L))
        assertTrue(deduplicator.shouldAccept(1_000_000_000L))
        assertFalse(deduplicator.shouldAccept(1_000_000_000L))
        assertFalse(deduplicator.shouldAccept(900_000_000L))
        assertTrue(deduplicator.shouldAccept(2_000_000_000L))
    }

    @Test
    fun resetStartsANewFixSequence() {
        val deduplicator = MonotonicFixDeduplicator()
        assertTrue(deduplicator.shouldAccept(1_000_000_000L))

        deduplicator.reset()

        assertTrue(deduplicator.shouldAccept(1_000_000_000L))
    }
}
