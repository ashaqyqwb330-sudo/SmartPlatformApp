package com.example.database

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.BuildConfig
import com.example.engine.BuilderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SmartRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val settingsDao = db.settingsDao()
    private val logEntryDao = db.logEntryDao()
    private val appFileDao = db.appFileDao()
    private val chatMessageDao = db.chatMessageDao()

    val settingsFlow: Flow<SettingsEntity?> = settingsDao.getSettingsFlow()
    val filesFlow: Flow<List<AppFileEntity>> = appFileDao.getAllFilesFlow()
    val logsFlow: Flow<List<LogEntryEntity>> = logEntryDao.getAllLogsFlow()
    val chatFlow: Flow<List<ChatMessageEntity>> = chatMessageDao.getAllMessagesFlow()

    init {
        // Initialize default settings on background thread if they do not exist
        CoroutineScope(Dispatchers.IO).launch {
            if (settingsDao.getSettings() == null) {
                val defaultDir = File(context.filesDir, "workspace").absolutePath
                settingsDao.insertOrUpdate(
                    SettingsEntity(
                        baseDir = defaultDir,
                        builderPrefix = "@builder",
                        executorPrefix = "@executor",
                        treedocPrefix = "@treedoc"
                    )
                )
                addLog("system", "تم تهيئة قاعدة البيانات بمسار افتراضي: $defaultDir", "info")
            }
        }
    }

    suspend fun getSettings(): SettingsEntity {
        return settingsDao.getSettings() ?: withContext(Dispatchers.IO) {
            val defaultDir = File(context.filesDir, "workspace").absolutePath
            val defaultSettings = SettingsEntity(
                baseDir = defaultDir,
                builderPrefix = "@builder",
                executorPrefix = "@executor",
                treedocPrefix = "@treedoc"
            )
            settingsDao.insertOrUpdate(defaultSettings)
            defaultSettings
        }
    }

    suspend fun updateSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.insertOrUpdate(settings)
        addLog("system", "تم تحديث الإعدادات بنجاح.", "info")
    }

    suspend fun addLog(tag: String, message: String, type: String = "info") = withContext(Dispatchers.IO) {
        logEntryDao.insertLog(
            LogEntryEntity(
                tag = tag,
                message = message,
                type = type
            )
        )
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logEntryDao.clearLogs()
    }

    suspend fun addFile(filepath: String, content: String, size: Long) = withContext(Dispatchers.IO) {
        appFileDao.insertFile(
            AppFileEntity(
                filepath = filepath,
                content = content,
                size = size
            )
        )
    }

    suspend fun deleteFile(filepath: String) = withContext(Dispatchers.IO) {
        appFileDao.deleteFile(filepath)
    }

    suspend fun clearFiles() = withContext(Dispatchers.IO) {
        appFileDao.clearFiles()
    }

    suspend fun addChatMessage(sender: String, message: String) = withContext(Dispatchers.IO) {
        chatMessageDao.insertMessage(
            ChatMessageEntity(
                sender = sender,
                message = message
            )
        )
    }

    suspend fun clearChatHistory() = withContext(Dispatchers.IO) {
        chatMessageDao.clearHistory()
    }

    /**
     * الدالة المركزية لمعالجة التوجيهات
     */
    suspend fun processDirectives(text: String, isManual: Boolean = false): List<com.example.engine.ProcessResult> = withContext(Dispatchers.IO) {
        val activeSettings = getSettings()
        
        // التحقق من تصفية البادئات
        val bPrefixes = listOf(activeSettings.builderPrefix)
        val ePrefixes = listOf(activeSettings.executorPrefix)
        val tPrefixes = listOf(activeSettings.treedocPrefix)

        addLog("system", "بدء تحليل التوجيهات... ${if (isManual) "[يدوي]" else "[حافظة]"}", "info")

        val engineSettings = mapOf(
            "base_dir" to activeSettings.baseDir,
            "directive_prefixes" to bPrefixes,
            "executor_prefixes" to ePrefixes,
            "treedoc_prefixes" to tPrefixes,
            "absolute_path_handling" to "relative",
            "executor_build_command" to "echo 'Gradle Build Completed'"
        )

        val engine = BuilderEngine(context, engineSettings)
        
        // ربط تسجيل الأحداث بقاعدة البيانات
        engine.logFunc = { logMsg ->
            CoroutineScope(Dispatchers.IO).launch {
                addLog("engine", logMsg, "info")
            }
        }

        try {
            val results = engine.processText(text)
            if (results.isEmpty()) {
                if (isManual) {
                    addLog("engine", "⚠️ لم يتم اكتشاف أي توجيهات صالحة في النص.", "info")
                }
            } else {
                for (res in results) {
                    val logType = if (res.message.startsWith("❌")) "error" else "success"
                    addLog(res.type, res.message, logType)

                    // إذا كانت العملية تابعة لـ builder وتمت كتابة ملف، نقوم بحفظ الملف في قاعدة البيانات
                    if (res.type == "builder" && logType == "success") {
                        val path = res.data?.get("path") ?: "unknown_file"
                        val content = res.data?.get("content") ?: ""
                        val sizeStr = res.data?.get("size") ?: "0"
                        val size = sizeStr.toLongOrNull() ?: 0L
                        addFile(path, content, size)
                    }

                    // بث إشعار للنظام بنجاح أو فشل المعالجة
                    showProcessingNotification(res.type, res.message)
                }
            }
            results
        } catch (e: Exception) {
            val errMsg = "فشل أثناء المعالجة: ${e.message}"
            addLog("system", errMsg, "error")
            showProcessingNotification("system", errMsg)
            emptyList()
        }
    }

    /**
     * يولد تقريراً شجرياً للمجلد الافتراضي وينسخه للحافظة تلقائياً
     */
    suspend fun runFastTreeReport(): String = withContext(Dispatchers.IO) {
        val s = getSettings()
        val baseDir = File(s.baseDir)
        baseDir.mkdirs()

        val engineSettings = mapOf(
            "base_dir" to s.baseDir,
            "treedoc_prefixes" to listOf(s.treedocPrefix)
        )
        val engine = BuilderEngine(context, engineSettings)
        
        val tree = engine.collectTreeData(baseDir, showSize = true, showMtime = true, showCount = true)
        val report = buildTextReport(baseDir.absolutePath, tree)
        
        withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("TreeDoc Fast Report", report))
                addLog("treedoc", "📂 تم توليد تقرير شجري سريع ونسخه للحافظة تلقائياً.", "success")
            } catch (e: Exception) {
                Log.e("SmartRepository", "Cannot write clipboard in bg", e)
            }
        }
        report
    }

    private fun buildTextReport(rootPath: String, tree: List<BuilderEngine.TreeNode>): String {
        val sb = java.lang.StringBuilder()
        sb.appendLine("📂 $rootPath")
        sb.appendLine("=".repeat(50))
        buildTextLines(tree, "", true, sb)
        return sb.toString()
    }

    private fun buildTextLines(nodes: List<BuilderEngine.TreeNode>, prefix: String, isLast: Boolean, sb: java.lang.StringBuilder) {
        for ((i, node) in nodes.withIndex()) {
            val isLastItem = i == nodes.size - 1
            val connector = if (isLastItem) "└── " else "├── "
            sb.append("$prefix$connector${node.name}")
            val extras = mutableListOf<String>()
            if (node.size != null) extras.add("[${node.size}]")
            if (node.mtime != null) extras.add("(${node.mtime})")
            if (node.count != null && node.type == "directory") extras.add("📁 ${node.count} عنصر")
            if (extras.isNotEmpty()) sb.append(" ${extras.joinToString(" ")}")
            sb.appendLine()
            if (node.children.isNotEmpty()) {
                val newPrefix = prefix + if (isLastItem) "    " else "│   "
                buildTextLines(node.children, newPrefix, isLastItem, sb)
            }
        }
    }

    private fun showProcessingNotification(type: String, message: String) {
        val channelId = "smart_platform_processing"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات المنصة الذكية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات نتائج معالجة التوجيهات"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (type) {
            "builder" -> "🔨 منشئ الملفات"
            "executor" -> "⚡ منفذ الأوامر"
            "treedoc" -> "📂 مولد TreeDoc"
            else -> "📱 المنصة الذكية"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
