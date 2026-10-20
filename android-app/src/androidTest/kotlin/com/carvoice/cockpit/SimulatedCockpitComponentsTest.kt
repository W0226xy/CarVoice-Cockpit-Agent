package com.carvoice.cockpit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.carvoice.cockpit.model.SimulatedCockpitStateUi
import com.carvoice.cockpit.ui.components.SimulatedCockpitStateCard
import com.carvoice.cockpit.ui.theme.CarVoiceCockpitTheme
import org.junit.Rule
import org.junit.Test

class SimulatedCockpitComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stateCardDisplaysAllSimulatedCockpitFields() {
        composeRule.setContent {
            CarVoiceCockpitTheme {
                SimulatedCockpitStateCard(
                    SimulatedCockpitStateUi(
                        acEnabled = true,
                        targetTemperature = 24f,
                        fanLevel = 3,
                        frontDefrostEnabled = true,
                        driverWindowPercent = 50,
                        passengerWindowPercent = 20,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("模拟座舱状态").assertIsDisplayed()
        composeRule.onNodeWithText("24°C / 3 档").assertIsDisplayed()
        composeRule.onNodeWithText("主驾驶 50% · 副驾驶 20%").assertIsDisplayed()
    }
}
