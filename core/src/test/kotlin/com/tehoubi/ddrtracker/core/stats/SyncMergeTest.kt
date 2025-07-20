package com.tehoubi.ddrtracker.core.stats

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncMergeTest {
    @Test
    fun `newer remote rows win, ties stay local`() {
        assertTrue(SyncMerge.remoteWins(null, 5))
        assertTrue(SyncMerge.remoteWins(4, 5))
        assertFalse(SyncMerge.remoteWins(5, 5))
        assertFalse(SyncMerge.remoteWins(6, 5))
    }
}
