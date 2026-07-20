package com.aurora.modifypositioning

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.aurora.modifypositioning.domain.MockControllerStore
import com.aurora.modifypositioning.service.MockLocationService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetRuntimeState(context)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", true)
            .commit()
        composeRule.activityRule.scenario.recreate()
    }

    @After
    fun teardown() {
        resetRuntimeState(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun mainScreen_showsControlButtons() {
        composeRule.onNodeWithTag("map_start").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("map_pause").assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("map_stop").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun mainScreen_canOpenOnboarding() {
        composeRule.onNodeWithTag("nav_onboarding").performClick()
        composeRule.onNodeWithTag("onboarding_continue").assertIsDisplayed()
    }

    private fun resetRuntimeState(context: Context) {
        context.getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("keep_running", false)
            .commit()
        context.stopService(Intent(context, MockLocationService::class.java))
        MockControllerStore.instance.onServiceStopped()
    }
}
