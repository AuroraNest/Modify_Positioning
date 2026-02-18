package com.aurora.modifypositioning.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aurora.modifypositioning.MainActivity
import com.aurora.modifypositioning.R
import com.aurora.modifypositioning.domain.MockControllerStore
import com.aurora.modifypositioning.location.AndroidLocationInjector
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.ENHANCED_UPDATE_INTERVAL_MS
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.util.MockEnvironmentChecker

class MockLocationService : Service() {

    private lateinit var injector: AndroidLocationInjector
    private val controller = MockControllerStore.instance

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        injector = AndroidLocationInjector(
            context = this,
            updateIntervalMs = ENHANCED_UPDATE_INTERVAL_MS,
            onError = { error ->
                controller.onError(error)
                refreshNotification()
            },
            onInjected = { report ->
                controller.onInjected(report)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
            else -> Unit
        }
        return START_STICKY
    }

    private fun handleStart() {
        startForeground(NOTIFICATION_ID, buildNotification(getRunningContentText()))

        val missingPermissions = MockEnvironmentChecker.missingPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            controller.onError("缺少权限: ${missingPermissions.joinToString()}")
            refreshNotification()
            return
        }

        if (!MockEnvironmentChecker.isMockLocationAppSelected(this)) {
            controller.onError("请在开发者选项中将本应用设为模拟位置信息应用")
            refreshNotification()
            return
        }

        injector.start(DEFAULT_TARGET)
        controller.onServiceStarted(DEFAULT_TARGET)
        refreshNotification()
    }

    private fun handlePause() {
        if (controller.state.value == MockState.Idle) {
            return
        }
        injector.pause()
        controller.onServicePaused()
        refreshNotification()
    }

    private fun handleStop() {
        injector.stop()
        controller.onServiceStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        injector.dispose()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(resolveContentText()))
    }

    private fun resolveContentText(): String {
        return when (val state = controller.state.value) {
            MockState.Running -> getRunningContentText()
            MockState.Paused -> getPausedContentText()
            MockState.Idle -> "服务未运行"
            is MockState.Error -> "${getString(R.string.notification_content_error)}: ${state.message}"
        }
    }

    private fun getRunningContentText(): String {
        return getString(R.string.notification_content_running)
    }

    private fun getPausedContentText(): String {
        return getString(R.string.notification_content_paused)
    }

    private fun buildNotification(content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIFICATION_ID = 10001

        private const val ACTION_PREFIX = "com.aurora.modifypositioning.action"
        const val ACTION_START = "$ACTION_PREFIX.START"
        const val ACTION_PAUSE = "$ACTION_PREFIX.PAUSE"
        const val ACTION_STOP = "$ACTION_PREFIX.STOP"

        fun startIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).setAction(ACTION_START)
        }

        fun pauseIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).setAction(ACTION_PAUSE)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
