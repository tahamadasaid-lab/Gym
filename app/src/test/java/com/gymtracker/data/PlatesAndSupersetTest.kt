package com.gymtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatesAndSupersetTest {
    @Test
    fun plates_100kgOn20kgBar() {
        val load = computePlates(100.0, 20.0, StandardPlates)
        assertEquals(listOf(25.0, 15.0), load.perSide) // 40 kg par côté
        assertTrue(load.exact)
        assertEquals(100.0, load.achieved, 0.0)
    }

    @Test
    fun plates_smallIncrements() {
        val load = computePlates(62.5, 20.0, StandardPlates)
        assertEquals(listOf(20.0, 1.25), load.perSide) // 21,25 kg par côté
        assertTrue(load.exact)
    }

    @Test
    fun plates_notExactWithLimitedPlates() {
        val load = computePlates(61.0, 20.0, listOf(20.0, 10.0, 5.0))
        assertEquals(listOf(20.0), load.perSide)
        assertFalse(load.exact)
        assertEquals(60.0, load.achieved, 0.0)
        assertEquals(1.0, load.remainder, 1e-9)
    }

    @Test
    fun plates_belowBar() {
        val load = computePlates(15.0, 20.0, StandardPlates)
        assertTrue(load.belowBar)
        assertTrue(load.perSide.isEmpty())
    }

    @Test
    fun supersetBlocks_groupsConsecutiveSameGroup() {
        val groups = listOf<Long?>(null, 5, 5, 5, null, 9, 9, 5)
        val blocks = groups.withIndex().toList().supersetBlocks { it.value }.map { b -> b.map { it.index } }
        assertEquals(listOf(listOf(0), listOf(1, 2, 3), listOf(4), listOf(5, 6), listOf(7)), blocks)
    }
}
