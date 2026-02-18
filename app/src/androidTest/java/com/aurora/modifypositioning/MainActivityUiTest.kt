package com.aurora.modifypositioning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", true)
            .commit()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun mainScreen_showsControlButtons() {
        composeRule.onNodeWithText("开始模拟").assertIsDisplayed()
        composeRule.onNodeWithText("暂停").assertIsDisplayed()
        composeRule.onNodeWithText("停止").assertIsDisplayed()
    }

    @Test
    fun mainScreen_canOpenOnboarding() {
        composeRule.onNodeWithText("返回首次引导").performClick()
        composeRule.onNodeWithText("首次使用引导").assertIsDisplayed()
    }
}
