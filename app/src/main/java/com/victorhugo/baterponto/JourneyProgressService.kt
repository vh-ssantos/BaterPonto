package com.victorhugo.baterponto

import android.app.Service
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.LocalDateTime

/** User-visible, silent timer. Never holds a wake lock or keeps counting through breaks. */
class JourneyProgressService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastState = ""
    private val refresh = object : Runnable {
        override fun run() {
            val day = WorkDayStore(this@JourneyProgressService).load()
            val now = LocalDateTime.now()
            if (!progressVisible(day, now) || !JourneyNotification.allowed(this@JourneyProgressService)) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            val state = "${day.hashCode()}:${day.workedMinutes(now)}:${day.inStandardBreak(now)}"
            if (state != lastState) {
                try {
                    getSystemService(NotificationManager::class.java).notify(JourneyNotification.ID,
                        JourneyNotification.build(this@JourneyProgressService, day, now))
                    lastState = state
                } catch (_: SecurityException) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        JourneyNotification.channel(this)
        val day = WorkDayStore(this).load()
        val now = LocalDateTime.now()
        val notification = JourneyNotification.build(this, day, now)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(JourneyNotification.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(JourneyNotification.ID, notification)
        running = true
        lastState = ""
        handler.removeCallbacks(refresh)
        handler.post(refresh)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(refresh)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var running = false
            private set
    }
}
