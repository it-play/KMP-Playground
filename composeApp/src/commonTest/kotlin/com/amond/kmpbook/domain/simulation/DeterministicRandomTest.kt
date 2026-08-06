package com.amond.kmpbook.domain.simulation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeterministicRandomTest {
    @Test
    fun sameSeedProducesGoldenBitSequence() {
        val first = DeterministicRandom(0L)
        val second = DeterministicRandom(0L)

        val expected = longArrayOf(
            -2_152_535_657_050_944_081L,
            7_960_286_522_194_355_700L,
            487_617_019_471_545_679L,
        )
        assertContentEquals(expected, LongArray(3) { first.nextLong() })
        assertContentEquals(expected, LongArray(3) { second.nextLong() })
    }

    @Test
    fun snapshotRestoresExactContinuation() {
        val random = DeterministicRandom(42L)
        repeat(7) { random.nextLong() }
        val snapshot = random.snapshot()
        val continuation = List(20) { random.nextLong() }

        random.restore(snapshot)

        assertEquals(continuation, List(20) { random.nextLong() })
    }

    @Test
    fun forkIsRepeatableAndDoesNotConsumeParent() {
        val parent = DeterministicRandom(91L)
        val expectedParentFirst = DeterministicRandom(91L).nextLong()
        val forkA = parent.fork(7L)
        val forkB = parent.fork(7L)
        val otherFork = parent.fork(8L)

        assertEquals(expectedParentFirst, parent.nextLong())
        assertEquals(List(10) { forkA.nextLong() }, List(10) { forkB.nextLong() })
        assertNotEquals(forkA.nextLong(), otherFork.nextLong())
        assertTrue((0 until 1_000).all { DeterministicRandom(it.toLong()).nextDouble() in 0.0..<1.0 })
    }
}
