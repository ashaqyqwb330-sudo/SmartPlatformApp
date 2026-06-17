package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.database.SmartRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClipboardMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: SmartRepository
    private var clipboardManager: ClipboardManager? = null
    private var lastTextHash = 0

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        scope.launch {
            try {
                val clipData = clipboardManager?.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotEmpty() && text.hashCode() != lastTextHash) {
                        lastTextHash = text.hashCode()
                        
                        val settings = repository.getSettings()
                        
                        // التحقق من احتواء النص على بادئات نشطة
                        val prefixes = listOf(settings.builderPrefix, settings.executorPrefix, settings.treedocPrefix)
                        val hasPrefix = prefixes.any { p -> text.contains(p) }
                        
                        if (hasPrefix) {
                            Log.d("ClipboardMonitor", "اكتشاف نص توجيهي جديد في الحافظة")
                            repository.processDirectives(text, isManual = false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ClipboardMonitor", "Error listening clip changes", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SmartRepository(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        
        Log.d("ClipboardMonitor", "تم تشغيل الخدمة وبدء الاستماع للحافظة")
        scope.launch {
            repository.addLog("system", "تم بدء الخدمة الخلفية لمراقبة الحافظة الاستباقية.", "info")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "clipboard_monitor_background"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "مراقب الحافظة الخلفي",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يعمل في الخلفية لضمان مراقبة التوجيهات وتنفيذها فور النسخ"
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("المنصة الذكية نشطة")
            .setContentText("مراقبة الحافظة الخلفية مستمرة لمعالجة التوجيهات فورًا")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        scope.cancel()
        Log.d("ClipboardMonitor", "تم إيقاف الخدمة")
    }
}
