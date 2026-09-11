package dev.lukino.daybook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SmokeTest {
    @get:Rule(order = 0) val returningUser = ReturningUserRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun applicationStarts() { compose.onNodeWithText("Daybook").assertIsDisplayed() }
}
