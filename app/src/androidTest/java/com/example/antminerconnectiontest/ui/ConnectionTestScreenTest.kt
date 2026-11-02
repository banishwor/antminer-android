package com.example.antminerconnectiontest.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.antminerconnectiontest.theme.AntminerConnectionTestTheme
import org.junit.Rule
import org.junit.Test

class ConnectionTestScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun screenDisplays_allRequiredElements() {
        composeTestRule.setContent {
            AntminerConnectionTestTheme {
                ConnectionTestScreen()
            }
        }

        // Verify title
        composeTestRule.onNodeWithText("Antminer API Test").assertIsDisplayed()

        // Verify default field values / labels
        composeTestRule.onNodeWithText("Miner IP Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("192.168.1.2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("root").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()

        // Verify Action Button
        composeTestRule.onNodeWithText("TEST ALL ENDPOINTS").assertIsDisplayed()

        // Verify 6 endpoints are listed
        composeTestRule.onNodeWithText("/cgi-bin/get_system_info.cgi").assertIsDisplayed()
        composeTestRule.onNodeWithText("/cgi-bin/summary.cgi").assertIsDisplayed()
        composeTestRule.onNodeWithText("/cgi-bin/stats.cgi").assertIsDisplayed()
        composeTestRule.onNodeWithText("/cgi-bin/pools.cgi").assertIsDisplayed()
        composeTestRule.onNodeWithText("/cgi-bin/warning.cgi").assertIsDisplayed()
        composeTestRule.onNodeWithText("/cgi-bin/chart.cgi").assertIsDisplayed()
    }
}
