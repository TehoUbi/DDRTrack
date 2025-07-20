package com.tehoubi.ddrtracker.cloud

import com.tehoubi.ddrtracker.data.local.PlayEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirestoreMappingTest {

    private val play = PlayEntity(
        id = "p1", songKey = "paranoia", songTitle = "PARANOiA", artist = "180", style = "SINGLE", difficulty = "EXPERT",
        level = 14, score = 951_230, exScore = 1200, grade = "AA_PLUS", lamp = "CLEAR", marvelous = 300, perfect = 80,
        great = 10, good = 2, ok = 30, miss = 3, maxCombo = 200, fast = 4, slow = null, failed = false,
        playedAt = 1_776_654_000_000, sessionId = "s1", source = "CAMERA", scanConfidence = 0.91f, note = null,
        updatedAt = 1_776_654_100_000, deleted = false,
    )

    @Test
    fun `documents round-trip`() {
        // Firestore hands numbers back as Long / Double.
        val doc = play.toDocument()
        val back = playFromDocument("p1", doc)!!
        // Session ids are recomputed locally, not trusted from the cloud.
        assertEquals(play.copy(sessionId = null, cloudDirty = true), back.copy(cloudDirty = true))
        assertEquals(0.91f, back.scanConfidence!!, 1e-6f)
    }

    @Test
    fun `incomplete documents are skipped`() {
        assertNull(playFromDocument("p1", play.toDocument() - "score"))
    }
}
