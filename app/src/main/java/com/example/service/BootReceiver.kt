package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.database.SmartRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "تم إقلاع النظام، فحص تفعيل الخدمة التلقائية")
            val repository = SmartRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val settings = repository.getSettings()
                if (settings.autostartEnabled) {
                    val serviceIntent = Intent(context, ClipboardMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    repository.addLog("system", "تم تشغيل مراقب الحافظة تلقائياً مع بدء النظام [تشغيل تلقائي].", "info")
                }
            }
        }
    }
}
