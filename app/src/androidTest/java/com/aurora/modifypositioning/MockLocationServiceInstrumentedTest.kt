package com.aurora.modifypositioning

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.modifypositioning.service.MockLocationService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MockLocationServiceInstrumentedTest {

    @Test
    fun createNotificationChannel_registersChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        MockLocationService.createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(MockLocationService.CHANNEL_ID)
            assertNotNull(channel)
        } else {
            assertTrue(true)
        }
    }

    @Test
    fun serviceIntents_haveExpectedAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val start = MockLocationService.startIntent(context)
        val pause = MockLocationService.pauseIntent(context)
        val stop = MockLocationService.stopIntent(context)

        assertTrue(start.action == MockLocationService.ACTION_START)
        assertTrue(pause.action == MockLocationService.ACTION_PAUSE)
        assertTrue(stop.action == MockLocationService.ACTION_STOP)
    }
}
