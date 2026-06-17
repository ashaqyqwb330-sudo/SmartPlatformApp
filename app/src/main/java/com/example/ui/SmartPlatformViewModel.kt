package com.example.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.AppFileEntity
import com.example.database.ChatMessageEntity
import com.example.database.LogEntryEntity
import com.example.database.SettingsEntity
import com.example.database.SmartRepository
import com.example.service.ClipboardMonitorService
import com.example.service.OverlayBubbleService
import com.example.engine.GeminiService
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SmartPlatformViewModel(private val context: Context) : ViewModel() {
    private val repository = SmartRepository(context)
    private val geminiService = GeminiService()

    // الحالات المستمدة من قاعدة البيانات
    private val _settings = MutableStateFlow<SettingsEntity?>(null)
    val settings: StateFlow<SettingsEntity?> = _settings.asStateFlow()

    private val _files = MutableStateFlow<List<AppFileEntity>>(emptyList())
    val files: StateFlow<List<AppFileEntity>> = _files.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntryEntity>>(emptyList())
    val logs: StateFlow<List<LogEntryEntity>> = _logs.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _chatMessages.asStateFlow()

    // حالات الواجهات والتبويبات
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isBubbleRunning = MutableStateFlow(false)
    val isBubbleRunning: StateFlow<Boolean> = _isBubbleRunning.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isGeminiThinking = MutableStateFlow(false)
    val isGeminiThinking: StateFlow<Boolean> = _isGeminiThinking.asStateFlow()

    // مدخلات التوليد الفردي
    private val _treeReportPreview = MutableStateFlow("")
    val treeReportPreview: StateFlow<String> = _treeReportPreview.asStateFlow()

    init {
        // جمع التدفقات التفاعلية من قاعدة البيانات وتحديث الحالة فورياً
        viewModelScope.launch {
            repository.settingsFlow.collectLatest {
                _settings.value = it
                if (it != null) {
                    _isBubbleRunning.value = isServiceRunning(context, OverlayBubbleService::class.java.name)
                }
            }
        }

        viewModelScope.launch {
            repository.filesFlow.collectLatest {
                _files.value = it
            }
        }

        viewModelScope.launch {
            repository.logsFlow.collectLatest {
                _logs.value = it
            }
        }

        viewModelScope.launch {
            repository.chatFlow.collectLatest {
                _chatMessages.value = it
            }
        }

        updateServiceStatus()
    }

    /**
     * تحديث حالة التشغيل للخدمات المختلفة
     */
    fun updateServiceStatus() {
        _isServiceRunning.value = isServiceRunning(context, ClipboardMonitorService::class.java.name)
        _isBubbleRunning.value = isServiceRunning(context, OverlayBubbleService::class.java.name)
    }

    private fun isServiceRunning(context: Context, serviceName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * تشغيل وإيقاف مراقب الحافظة الاستباقي
     */
    fun toggleClipboardMonitor(active: Boolean) {
        val intent = Intent(context, ClipboardMonitorService::class.java)
        if (active) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isServiceRunning.value = true
        } else {
            context.stopService(intent)
            _isServiceRunning.value = false
            viewModelScope.launch {
                repository.addLog("system", "تم إيقاف الخدمة الخلفية لمراقبة الحافظة الاستباقية.", "info")
            }
        }
    }

    /**
     * تشغيل وإيقاف الفقاعة العائمة
     */
    fun toggleOverlayBubble(active: Boolean, permissionGranted: Boolean) {
        if (active && !permissionGranted) {
            viewModelScope.launch {
                repository.addLog("system", "صلاحية تراكب النوافذ مطلوبة لتشغيل الفقاعة العائمة.", "error")
            }
            return
        }

        val intent = Intent(context, OverlayBubbleService::class.java)
        if (active) {
            context.startService(intent)
            _isBubbleRunning.value = true
        } else {
            context.stopService(intent)
            _isBubbleRunning.value = false
            viewModelScope.launch {
                repository.addLog("system", "تم إيقاف الفقاعة العائمة.", "info")
            }
        }
    }

    /**
     * حفظ وتحديث الإعدادات
     */
    fun saveSettings(updated: SettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(updated)
            // إذا تغيرت الفقاعة من الإعدادات نقوم بتنشيطها أو تعطيلها
            if (updated.bubbleEnabled && !_isBubbleRunning.value) {
                if (Settings.canDrawOverlays(context)) {
                    toggleOverlayBubble(true, true)
                }
            } else if (!updated.bubbleEnabled && _isBubbleRunning.value) {
                toggleOverlayBubble(false, true)
            }
        }
    }

    /**
     * معالجة نص بشكل يدوي (لصق مباشر بالمحرر)
     */
    fun processManualText(text: String) {
        if (text.isBlank()) return
        _isProcessing.value = true
        viewModelScope.launch {
            repository.processDirectives(text, isManual = true)
            _isProcessing.value = false
        }
    }

    /**
     * تنفيذ أمر يدوي من قبل المستخدم
     */
    fun runManualCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            repository.addLog("executor", "بدء تنفيذ أمر يدوي: $command", "info")
            val activeSettings = repository.getSettings()
            val engineSettings = mapOf(
                "base_dir" to activeSettings.baseDir,
                "executor_build_command" to "echo 'Gradle Build Completed'"
            )
            val engine = com.example.engine.BuilderEngine(context, engineSettings)
            val result = engine.executeDirective(command)
            repository.addLog("executor", result, if (result.startsWith("❌")) "error" else "success")
        }
    }

    /**
     * توليد تقرير TreeDoc يدوي للمجلد المحدد
     */
    fun generateTreeReport(
        folderPath: String,
        format: String,
        includeSize: Boolean,
        includeTime: Boolean,
        includeCount: Boolean
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            repository.addLog("treedoc", "توليد تقرير شجيري يدوي لـ: $folderPath بصيغة $format", "info")
            
            val activeSettings = repository.getSettings()
            val engineSettings = mapOf(
                "base_dir" to activeSettings.baseDir,
                "treedoc_prefixes" to listOf(activeSettings.treedocPrefix)
            )
            val engine = com.example.engine.BuilderEngine(context, engineSettings)

            val baseDir = File(activeSettings.baseDir)
            val target = if (folderPath.isBlank() || folderPath == ".") {
                baseDir
            } else {
                File(folderPath).let {
                    if (it.isAbsolute && it.exists()) it else File(baseDir, folderPath)
                }
            }

            if (!target.exists()) {
                target.mkdirs()
            }

            try {
                val tree = engine.collectTreeData(
                    target,
                    showSize = includeSize,
                    showMtime = includeTime,
                    showCount = includeCount
                )

                val report = if (format.lowercase() == "json") {
                    "{\n  \"tree\": [\n" + tree.map { "    { \"name\": \"${it.name}\", \"type\": \"${it.type}\", \"size\": \"${it.size}\" }" }.joinToString(",\n") + "\n  ]\n}"
                } else {
                    val sb = StringBuilder()
                    sb.appendLine("📂 ${target.absolutePath}")
                    sb.appendLine("=".repeat(50))
                    
                    fun buildTextLines(nodes: List<com.example.engine.BuilderEngine.TreeNode>, prefix: String, isLast: Boolean) {
                        for ((i, node) in nodes.withIndex()) {
                            val isLastItem = i == nodes.size - 1
                            val connector = if (isLastItem) "└── " else "├── "
                            sb.append("$prefix$connector${node.name}")
                            val extras = mutableListOf<String>()
                            if (includeSize && node.size != null) extras.add("[${node.size}]")
                            if (includeTime && node.mtime != null) extras.add("(${node.mtime})")
                            if (includeCount && node.count != null && node.type == "directory") extras.add("📁 ${node.count} عنصر")
                            if (extras.isNotEmpty()) sb.append(" ${extras.joinToString(" ")}")
                            sb.appendLine()
                            if (node.children.isNotEmpty()) {
                                val newPrefix = prefix + if (isLastItem) "    " else "│   "
                                buildTextLines(node.children, newPrefix, isLastItem)
                            }
                        }
                    }
                    buildTextLines(tree, "", true)
                    sb.toString()
                }

                _treeReportPreview.value = report
                
                // حفظ التقرير كملف
                val reportFile = File(target, "tree_report.${format.lowercase()}")
                reportFile.writeText(report, Charsets.UTF_8)
                repository.addFile(reportFile.name, report, reportFile.length())
                
                repository.addLog("treedoc", "✅ تم توليد تقرير شجرة بنجاح وحفظه في: ${reportFile.name}", "success")
            } catch (e: Exception) {
                repository.addLog("treedoc", "❌ فشل توليد التقرير: ${e.message}", "error")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * إرسال رسالة إلى Gemini AI
     */
    fun sendMessageToGemini(promptText: String) {
        if (promptText.isBlank()) return
        val prompt = promptText.trim()

        viewModelScope.launch {
            // 1. حفظ رسالة المستخدم
            repository.addChatMessage("user", prompt)
            _isGeminiThinking.value = true

            // 2. تحضير سجل المحادثة
            val activeChat = _chatMessages.value
            val history = activeChat.map { Pair(it.sender, it.message) }

            // 3. تهيئة التعليمات البرمجية لتوجيه Gemini ليتجاوب بصيغة توجيهات صالحة
            val activeSettings = repository.getSettings()
            val sysInstruction = """
                أنت المنصة الذكية (Smart Platform) المساعد البرمجي الخارق على نظام أندرويد.
                سيوجه لك المستخدم أسئلة أو طلبات لإنشاء وتعديل ملفات برمجية أو تنفيذ أوامر برمجية.
                يجب أن تقوم بتوليد الإجابة بنص منسق يحتوي على توجيهات صالحة للمنصة الذكية بالبادئات التالية:
                - للبناء وإنشاء الملفات، استخدم بادئة البناء والوضع:
                  ${activeSettings.builderPrefix}:file [اسم_المسار_النسبي]
                  محتويات الملف هنا...
                  ${activeSettings.builderPrefix}:end
                - لتنفيذ الأوامر، استخدم:
                  ${activeSettings.executorPrefix}:[الأمر]
                - لتوليد تقرير المجلد أو شجرة الملفات، استخدم:
                  ${activeSettings.treedocPrefix}:report [اسم_المجلد] [الصيغة] [clipboard]

                مثال لتوليد كود لشخص:
                ${activeSettings.builderPrefix}:file index.html
                <h1>Hello</h1>
                ${activeSettings.builderPrefix}:end

                يرجى جعل الرد باللغة العربية مع الحفاظ على أنماط الأكواد الإنجليزية. تأكد من إغلاق التوجيهات بـ :end تمامًا لتنفيذ الأتمتة المباشرة!
            """.trimIndent()

            val result = geminiService.chatWithGemini(prompt, sysInstruction, history)
            _isGeminiThinking.value = false

            result.onSuccess { reply ->
                // 4. حفظ رد Gemini
                repository.addChatMessage("gemini", reply)

                // 5. التحقق مما إذا كان يحتوي على توجيهات صالحة لتنفيذها تلقائياً!
                val prefixes = listOf(activeSettings.builderPrefix, activeSettings.executorPrefix, activeSettings.treedocPrefix)
                val hasDirectives = prefixes.any { p -> reply.contains(p) }
                if (hasDirectives) {
                    repository.addLog("system", "🤖 تم كشف توجيهات صالحة في رد Gemini، جاري المعالجة التلقائية... 🔥", "info")
                    repository.processDirectives(reply, isManual = true)
                }
            }.onFailure { exception ->
                val errMsg = exception.message ?: "خطأ غير معروف في شبكة الاتصال"
                repository.addChatMessage("gemini", "❌ فشل الاتصال بخوادم الذكاء الاصطناعي: $errMsg")
                repository.addLog("system", "فشل اتصال Gemini: $errMsg", "error")
            }
        }
    }

    /**
     * حذف ملف مادي ومسحه من قاعدة البيانات
     */
    fun deleteAppFile(filepath: String) {
        viewModelScope.launch {
            try {
                val activeSettings = repository.getSettings()
                val fullFile = File(activeSettings.baseDir, filepath)
                if (fullFile.exists()) {
                    fullFile.delete()
                }
                repository.deleteFile(filepath)
                repository.addLog("builder", "🗑️ تم حذف الملف: $filepath", "info")
            } catch (e: Exception) {
                repository.addLog("builder", "❌ فشل حذف الملف: ${e.message}", "error")
            }
        }
    }

    /**
     * حفظ وتخزين كود معدل في الملف
     */
    fun saveFileContent(filepath: String, content: String) {
        viewModelScope.launch {
            try {
                val activeSettings = repository.getSettings()
                val fullFile = File(activeSettings.baseDir, filepath)
                fullFile.parentFile?.mkdirs()
                fullFile.writeText(content, Charsets.UTF_8)
                repository.addFile(filepath, content, fullFile.length())
                repository.addLog("builder", "💾 تم تعديل وحفظ محتوى الملف: $filepath يدوياً.", "success")
            } catch (e: Exception) {
                repository.addLog("builder", "❌ فشل حفظ تعديل الملف: ${e.message}", "error")
            }
        }
    }

    /**
     * مسح السجلات بالكامل
     */
    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    /**
     * مسح تاريخ الشات
     */
    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }
}

class SmartPlatformViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmartPlatformViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SmartPlatformViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
