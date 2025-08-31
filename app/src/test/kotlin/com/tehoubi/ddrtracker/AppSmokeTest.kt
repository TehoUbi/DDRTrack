package com.tehoubi.ddrtracker

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starts the real app (Hilt graph, Room, WorkManager, navigation) and logs
 * a play by hand, the path a new user without a cabinet nearby takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun type(label: String, text: String) {
        compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(text)
    }

    @Test
    fun `log a play by hand and see it everywhere`() {
        // Room loads off the main thread, so wait for the first emission.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No plays yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add by hand").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Add a play").fetchSemanticsNodes().isNotEmpty() }

        type("Song", "Butterfly")
        compose.onNodeWithText("Expert").performClick()
        type("Level", "12")
        type("Score", "999880")
        type("MARVELOUS", "452")
        type("PERFECT", "12")
        type("GREAT", "0")
        type("GOOD", "0")
        type("O.K.", "34")
        type("MISS", "0")
        compose.onNodeWithText("Save play").performScrollTo().performClick()

        // Saving opens the play.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Chart history (1 play)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Perfect Full Combo").assertExists()
        compose.onNodeWithText("EX 1482 · 99.2%").assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Recent plays").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Butterfly").assertExists()

        compose.onNodeWithText("Progress").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Best clear by level").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Compare").performClick()
        compose.onNodeWithText("Set up in Settings").assertExists()
    }
}
