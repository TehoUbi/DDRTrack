package com.tehoubi.ddrtracker.ui.charts

import kotlin.test.Test
import kotlin.test.assertEquals

class NiceTicksTest {
    @Test
    fun `ticks land on round numbers covering the range`() {
        assertEquals(listOf(0f, 5f, 10f, 15f, 20f), niceTicks(0f, 17f))
        assertEquals(listOf(80f, 85f, 90f, 95f), niceTicks(81.2f, 94.8f))
        assertEquals(listOf(800_000f, 850_000f, 900_000f, 950_000f, 1_000_000f), niceTicks(812_340f, 991_000f))
    }

    @Test
    fun `integer data never repeats a label`() {
        assertEquals(listOf(12f, 13f, 14f), niceTicks(12f, 14f, integer = true))
    }
}
