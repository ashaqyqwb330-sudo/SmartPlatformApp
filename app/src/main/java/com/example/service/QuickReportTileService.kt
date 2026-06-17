package com.example.service

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.database.SmartRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickReportTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var repository: SmartRepository

    override fun onCreate() {
        super.onCreate()
        repository = SmartRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "تقرير سريع 📂"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "توليد شجرة مجلدات"
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile

        // التقرير السريع قيد التشغيل يدوياً

        scope.launch(Dispatchers.IO) {
            try {
                repository.addLog("system", "تم استدعاء البلاطة السريعة لتوليد التقرير السريع.", "info")
                val report = repository.runFastTreeReport()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QuickReportTileService, "تم نسخ تقرير الشجرة للحافظة! 📋", Toast.LENGTH_LONG).show()
                    tile?.state = Tile.STATE_ACTIVE
                    tile?.updateTile()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QuickReportTileService, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                    tile?.state = Tile.STATE_ACTIVE
                    tile?.updateTile()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
