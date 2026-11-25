package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LedgerFlow", appName)
    }

    @Test
    fun `launch main activity and navigate all tabs and appearance sheet without crashing`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_appearance_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance_done_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_currency_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("currency_option_inr").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_done_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_budgets").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_reports").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_recurring").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_categories").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_expenses").performClick()
        composeRule.waitForIdle()
    }
}
