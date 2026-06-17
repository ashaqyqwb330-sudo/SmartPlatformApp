package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainActivity
import com.example.BuildConfig
import com.example.database.AppFileEntity
import com.example.database.ChatMessageEntity
import com.example.database.LogEntryEntity
import com.example.database.SettingsEntity
import com.example.service.ClipboardMonitorService
import com.example.service.OverlayBubbleService
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlatformApp(
    viewModel: SmartPlatformViewModel = viewModel(factory = SmartPlatformViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val settingsState by viewModel.settings.collectAsState()
    val filesList by viewModel.files.collectAsState()
    val logsList by viewModel.logs.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isBubbleRunning by viewModel.isBubbleRunning.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isGeminiThinking by viewModel.isGeminiThinking.collectAsState()
    val treeReportPreview by viewModel.treeReportPreview.collectAsState()

    var currentTab by remember { mutableStateOf(0) }
    var showTileDrawer by remember { mutableStateOf(false) }

    // التحكم بصلاحيات النظام
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.updateServiceStatus()
    }

    val requestManageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    // التحقق المبدئي ومنح الصلاحيات
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // فحص توافر صلاحية التراكب والتخزين ومطالبتها بلباقة عند البدء
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!Settings.canDrawOverlays(context)) {
                showPermissionDialog = true
            }
        }
    }

    // شاشة طلب الصلاحيات
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("مطلوب صلاحيات ممتازة 🔐") },
            text = {
                Column {
                    Text("لتشغيل ميزات المنصة الذكيه بكامل فاعليتها، نحتاج لتفعيل الصلاحيات التالية:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• الظهور فوق التطبيقات (لتفعيل الاختصار العائم الذهبي)")
                    Text("• الوصول لكامل الملفات (لكتابة وتحرير الأكواد والمشاريع)")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        // طلب تراكب النوافذ
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayLauncher.launch(intent)
                            }
                        }
                        // طلب إدارة الملفات
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            requestManageStorageLauncher.launch(intent)
                        }
                    }
                ) {
                    Text("الموافقة والتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("ليس الآن")
                }
            }
        )
    }

    val secureSettings = settingsState ?: SettingsEntity(baseDir = context.filesDir.absolutePath)

    // تدرج لوني فخم للوضع الذهبي أو العادي
    val appBackgroundBrush = if (secureSettings.goldenMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF151515),
                Color(0xFF252118),
                Color(0xFF1C1810)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush)
    ) {
        if (secureSettings.goldenMode) {
            // ---- الوضع الذهبي المُحاكى (إطار محاكاة فخم) ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp)
                    .border(
                        width = 4.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFD54F),
                                Color(0xFFFFB300),
                                Color(0xFFE65100),
                                Color(0xFFFFB300),
                                Color(0xFFFFECB3)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF121212))
            ) {
                // شريط الحالة التفاعلي
                SimulatedStatusBar(
                    isMonitorActive = isServiceRunning,
                    isBubbleActive = isBubbleRunning,
                    onStatusBarClick = { showTileDrawer = !showTileDrawer }
                )

                Box(modifier = Modifier.weight(1f)) {
                    // المحتوى الفعلي للتطبيق
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xFF1E1E1E),
                                contentColor = Color(0xFFFFB300),
                                modifier = Modifier.testTag("golden_nav_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    icon = { Icon(Icons.Default.Radar, contentDescription = "المراقب") },
                                    label = { Text("المراقب", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF121212),
                                        unselectedIconColor = Color(0xFF888888),
                                        selectedTextColor = Color(0xFFFFB300),
                                        unselectedTextColor = Color(0xFF888888),
                                        indicatorColor = Color(0xFFFFB300)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    icon = { Icon(Icons.Default.List, contentDescription = "TreeDoc") },
                                    label = { Text("TreeDoc", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF121212),
                                        unselectedIconColor = Color(0xFF888888),
                                        selectedTextColor = Color(0xFFFFB300),
                                        unselectedTextColor = Color(0xFF888888),
                                        indicatorColor = Color(0xFFFFB300)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    icon = { Icon(Icons.Default.Terminal, contentDescription = "المنفذ") },
                                    label = { Text("المنفذ", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF121212),
                                        unselectedIconColor = Color(0xFF888888),
                                        selectedTextColor = Color(0xFFFFB300),
                                        unselectedTextColor = Color(0xFF888888),
                                        indicatorColor = Color(0xFFFFB300)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Gemini") },
                                    label = { Text("Gemini", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF121212),
                                        unselectedIconColor = Color(0xFF888888),
                                        selectedTextColor = Color(0xFFFFB300),
                                        unselectedTextColor = Color(0xFF888888),
                                        indicatorColor = Color(0xFFFFB300)
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == 4,
                                    onClick = { currentTab = 4 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                                    label = { Text("الإعدادات", fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF121212),
                                        unselectedIconColor = Color(0xFF888888),
                                        selectedTextColor = Color(0xFFFFB300),
                                        unselectedTextColor = Color(0xFF888888),
                                        indicatorColor = Color(0xFFFFB300)
                                    )
                                )
                            }
                        },
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AppScreenSelector(
                                tab = currentTab,
                                secureSettings = secureSettings,
                                files = filesList,
                                logs = logsList,
                                chat = chatMessages,
                                treePreview = treeReportPreview,
                                isMonitorOn = isServiceRunning,
                                isBubbleOn = isBubbleRunning,
                                isProgressActive = isProcessing,
                                isThinking = isGeminiThinking,
                                viewModel = viewModel
                            )
                        }
                    }

                    // شريط البلاطات السريع والمنسدل (Quick Tiles Panel)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTileDrawer,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SimulatedQuickTilesDrawer(
                            isMonitorActive = isServiceRunning,
                            isBubbleActive = isBubbleRunning,
                            onMonitorToggle = { viewModel.toggleClipboardMonitor(it) },
                            onBubbleToggle = {
                                if (Settings.canDrawOverlays(context)) {
                                    viewModel.toggleOverlayBubble(it, true)
                                } else {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayLauncher.launch(intent)
                                }
                            },
                            onQuickReportClick = {
                                viewModel.generateTreeReport(".", "HTML", true, true, true)
                                showTileDrawer = false
                            },
                            onClose = { showTileDrawer = false }
                        )
                    }
                }
            }
        } else {
            // ---- الوضع العادي (Material 3 مع Bottom Navigation) ----
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "المنصة الذكية",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        },
                        actions = {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 12.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.testTag("normal_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = { Icon(Icons.Default.Radar, contentDescription = "المراقب") },
                            label = { Text("المراقب") }
                        )
                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = { Icon(Icons.Default.List, contentDescription = "TreeDoc") },
                            label = { Text("TreeDoc") }
                        )
                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = { Icon(Icons.Default.Terminal, contentDescription = "المنفذ") },
                            label = { Text("المنفذ") }
                        )
                        NavigationBarItem(
                            selected = currentTab == 3,
                            onClick = { currentTab = 3 },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Gemini") },
                            label = { Text("Gemini") }
                        )
                        NavigationBarItem(
                            selected = currentTab == 4,
                            onClick = { currentTab = 4 },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                            label = { Text("الإعدادات") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AppScreenSelector(
                        tab = currentTab,
                        secureSettings = secureSettings,
                        files = filesList,
                        logs = logsList,
                        chat = chatMessages,
                        treePreview = treeReportPreview,
                        isMonitorOn = isServiceRunning,
                        isBubbleOn = isBubbleRunning,
                        isProgressActive = isProcessing,
                        isThinking = isGeminiThinking,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppScreenSelector(
    tab: Int,
    secureSettings: SettingsEntity,
    files: List<AppFileEntity>,
    logs: List<LogEntryEntity>,
    chat: List<ChatMessageEntity>,
    treePreview: String,
    isMonitorOn: Boolean,
    isBubbleOn: Boolean,
    isProgressActive: Boolean,
    isThinking: Boolean,
    viewModel: SmartPlatformViewModel
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "tabChange"
    ) { currentTab ->
        when (currentTab) {
            0 -> ObserverScreen(
                files = files,
                logs = logs,
                isMonitorActive = isMonitorOn,
                isProgress = isProgressActive,
                onMonitorChange = { viewModel.toggleClipboardMonitor(it) },
                onManualProcess = { viewModel.processManualText(it) },
                onDeleteFile = { viewModel.deleteAppFile(it) },
                onEditSaveFile = { path, content -> viewModel.saveFileContent(path, content) },
                onClearAllLogs = { viewModel.clearAllLogs() },
                secureSettings = secureSettings
            )
            1 -> TreeDocScreen(
                previewText = treePreview,
                onGenerate = { path, fmt, s, t, c -> viewModel.generateTreeReport(path, fmt, s, t, c) },
                secureSettings = secureSettings
            )
            2 -> ExecutorScreen(
                logs = logs,
                onExecute = { viewModel.runManualCommand(it) }
            )
            3 -> GeminiChatScreen(
                chatMessage = chat,
                isThinking = isThinking,
                onSend = { viewModel.sendMessageToGemini(it) },
                onClear = { viewModel.clearChatHistory() }
            )
            4 -> SettingsScreen(
                settings = secureSettings,
                isMonitorOn = isMonitorOn,
                isBubbleOn = isBubbleOn,
                onSave = { viewModel.saveSettings(it) },
                viewModel = viewModel
            )
        }
    }
}

// =====================================================================
// 1. شاشة المراقب (Observer Screen)
// =====================================================================

@Composable
fun ObserverScreen(
    files: List<AppFileEntity>,
    logs: List<LogEntryEntity>,
    isMonitorActive: Boolean,
    isProgress: Boolean,
    onMonitorChange: (Boolean) -> Unit,
    onManualProcess: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onEditSaveFile: (String, String) -> Unit,
    onClearAllLogs: () -> Unit,
    secureSettings: SettingsEntity
) {
    var manualPastedText by remember { mutableStateOf("") }
    var editingFile by remember { mutableStateOf<AppFileEntity?>(null) }
    var fileEditContent by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // بطاقة التحكم بمراقب الحافظة الخلفي والمستمر
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (secureSettings.goldenMode) Color(0xFF221F1A) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "المراقبة التلقائية الاستباقية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (secureSettings.goldenMode) Color(0xFFFFB300) else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isMonitorActive) "الخدمة نشطة وتستمع للحافظة بالخلفية..." else "الخدمة متوقفة حاليًا.",
                            fontSize = 12.sp,
                            color = if (secureSettings.goldenMode) Color(0xFFBCAAA4) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMonitorActive,
                        onCheckedChange = onMonitorChange,
                        modifier = Modifier.testTag("service_toggle_switch")
                    )
                }
            }
        }

        // محرر المريض لكتابة التوجيهات يدوياً ومعالجتها
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (secureSettings.goldenMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "معالجة التوجيهات يدويًا 📝",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (secureSettings.goldenMode) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualPastedText,
                        onValueChange = { manualPastedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("manual_input_editor"),
                        placeholder = { Text("ألصق الكود أو توجيهات @builder هنا...", fontSize = 13.sp) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onManualProcess(manualPastedText)
                            manualPastedText = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("process_token_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (secureSettings.goldenMode) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                            contentColor = if (secureSettings.goldenMode) Color.Black else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Text("معالجة النص الآن 🔥", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // بطاقة عرض الملفات المنشأة بواسطة builder
        item {
            Text(
                "الملفات المُنشأة والمحفوظة (${files.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (secureSettings.goldenMode) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface
            )
        }

        if (files.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "لا ملفات مخزنة حتى الآن. انسخ أي كود يحتوي على @builder لتراه هنا!",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else {
            items(files) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (secureSettings.goldenMode) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (file.filepath.endsWith(".html")) Icons.Default.Html else Icons.Default.Code,
                                    contentDescription = null,
                                    tint = if (secureSettings.goldenMode) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    file.filepath,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (secureSettings.goldenMode) Color.White else Color.Unspecified
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "الحجم: ${file.size} بايت | تم الحفظ: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(file.timestamp))}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    editingFile = file
                                    fileEditContent = file.content
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color.Gray)
                            }
                            IconButton(onClick = { onDeleteFile(file.filepath) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        // سجل الأحداث والعمليات (System Event Logs)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "سجل العمليات والأحداث 📋",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (secureSettings.goldenMode) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onClearAllLogs) {
                    Text("مسح السجل")
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    "السجل فارغ حالياً.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(logs.take(15)) { logEntry ->
                val logColor = when (logEntry.type) {
                    "error" -> Color(0xFFEF5350)
                    "success" -> Color(0xFF66BB6A)
                    else -> if (secureSettings.goldenMode) Color(0xFFBCAAA4) else Color.Gray
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(logEntry.timestamp))}] ",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                    Text(
                        "${logEntry.tag.uppercase()}: ",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = logColor
                    )
                    Text(
                        logEntry.message,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (secureSettings.goldenMode) Color.White else Color.Unspecified,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // بمثابة نافذة تحرير الملف
    if (editingFile != null) {
        AlertDialog(
            onDismissRequest = { editingFile = null },
            title = { Text("تحرير ملف: ${editingFile?.filepath}") },
            text = {
                OutlinedTextField(
                    value = fileEditContent,
                    onValueChange = { fileEditContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingFile?.let {
                            onEditSaveFile(it.filepath, fileEditContent)
                        }
                        editingFile = null
                    }
                ) {
                    Text("حفظ الملف 💾")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFile = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// =====================================================================
// 2. شاشة TreeDoc (TreeDoc Screen)
// =====================================================================

@Composable
fun TreeDocScreen(
    previewText: String,
    onGenerate: (String, String, Boolean, Boolean, Boolean) -> Unit,
    secureSettings: SettingsEntity
) {
    var folderPath by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("HTML") }
    var includeSize by remember { mutableStateOf(true) }
    var includeTime by remember { mutableStateOf(true) }
    var includeCount by remember { mutableStateOf(true) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (secureSettings.goldenMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "توليد مخطط الشجرة الهرمي TreeDoc 📂",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (secureSettings.goldenMode) Color(0xFFFFB300) else Color.Unspecified
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = folderPath,
                        onValueChange = { folderPath = it },
                        label = { Text("مسار المجلد داخل مساحة العمل") },
                        placeholder = { Text(". (للمجلد الافتراضي)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("صيغة التقرير:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("HTML", "MD", "TXT", "JSON").forEach { option ->
                            FilterChip(
                                selected = format == option,
                                onClick = { format = option },
                                label = { Text(option) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("خيارات التفاصيل:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeSize, onCheckedChange = { includeSize = it })
                        Text("عرض الأحجام", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Checkbox(checked = includeTime, onCheckedChange = { includeTime = it })
                        Text("تاريخ التعديل", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Checkbox(checked = includeCount, onCheckedChange = { includeCount = it })
                        Text("عدد العناصر", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onGenerate(folderPath, format, includeSize, includeTime, includeCount) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_tree_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (secureSettings.goldenMode) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                            contentColor = if (secureSettings.goldenMode) Color.Black else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("توليد التقرير الآن 📂", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // معاينة التقرير المنشأ
        if (previewText.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("معاينة التقرير المنسق 👁️", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(previewText))
                                    Toast.makeText(context, "تم النسخ للحافظة! 📋", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text("نسخ للحافظة 📋", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = previewText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF64FFDA),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// 3. شاشة المنفذ (Executor Screen)
// =====================================================================

@Composable
fun ExecutorScreen(
    logs: List<LogEntryEntity>,
    onExecute: (String) -> Unit
) {
    var cmdText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("منفذ الأوامر الفوري @executor ⚡", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = cmdText,
                    onValueChange = { cmdText = it },
                    placeholder = { Text("أدخل أمر التنفيذ هنا (مثل build أو open index.html)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onExecute(cmdText)
                        cmdText = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("execute_cmd_btn")
                ) {
                    Text("تنفيذ الأمر 🛠️", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("سجل تنفيذ الأوامر الأخير:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val execLogs = logs.filter { it.tag == "executor" }
            if (execLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد أوامر منفذة مؤخراً.", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(execLogs) { item ->
                        Text(
                            text = "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))}] ${item.message}",
                            color = if (item.message.startsWith("❌")) Color(0xFFEF5350) else Color(0xFF81C784),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// 4. شاشة Gemini AI Chatbot (Gemini Chat Screen)
// =====================================================================

@Composable
fun GeminiChatScreen(
    chatMessage: List<ChatMessageEntity>,
    isThinking: Boolean,
    onSend: (String) -> Unit,
    onClear: () -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // تمرير تلقائي لنهاية الرسائل
    LaunchedEffect(chatMessage.size) {
        if (chatMessage.isNotEmpty()) {
            listState.animateScrollToItem(chatMessage.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("مساعد Gemini البرمجي الذكي 🤖", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            TextButton(onClick = onClear) {
                Text("مسح المحادثة")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // منطقة عرض الرسائل
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF151515).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            if (chatMessage.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFFFB300))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "اكتب لـ Gemini مثل:\n'أنشئ ملف HTML بسيطاً يرحب بجمهور الأندرويد'",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatMessage) { msg ->
                        val isUser = msg.sender == "user"
                        val alignment = if (isUser) Alignment.End else Alignment.Start
                        val bubbleColor = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color(0xFF333333)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = alignment
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bubbleColor)
                                    .padding(12.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(
                                    msg.message,
                                    fontSize = 12.sp,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                )
                            }
                            Text(
                                text = if (isUser) "أنت" else "Gemini Flash 3.5",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                            )
                        }
                    }

                    if (isThinking) {
                        item {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF222222))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري معالجة طلبك المعقد وتوليد الأكواد...", fontSize = 11.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // مدخل الرسالة الجديد
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                placeholder = { Text("اطلب من Gemini توليد التوجيهات...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text_field"),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (promptInput.isNotBlank()) {
                        onSend(promptInput)
                        promptInput = ""
                    }
                },
                shape = CircleShape,
                modifier = Modifier.testTag("send_btn"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Send, contentDescription = "إرسال")
            }
        }
    }
}

// =====================================================================
// 5. شاشة الإعدادات والتفضيلات (Settings Screen)
// =====================================================================

@Composable
fun SettingsScreen(
    settings: SettingsEntity,
    isMonitorOn: Boolean,
    isBubbleOn: Boolean,
    onSave: (SettingsEntity) -> Unit,
    viewModel: SmartPlatformViewModel
) {
    var builderPrefix by remember { mutableStateOf(settings.builderPrefix) }
    var executorPrefix by remember { mutableStateOf(settings.executorPrefix) }
    var treedocPrefix by remember { mutableStateOf(settings.treedocPrefix) }
    var bDir by remember { mutableStateOf(settings.baseDir) }
    var autostart by remember { mutableStateOf(settings.autostartEnabled) }
    var ignoreBattery by remember { mutableStateOf(settings.ignoreBatteryEnabled) }
    var bubbleEnabled by remember { mutableStateOf(settings.bubbleEnabled) }
    var goldenMode by remember { mutableStateOf(settings.goldenMode) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("مساحات العمل وإعدادات المسار:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = bDir,
                        onValueChange = { bDir = it },
                        label = { Text("مجلد الحفظ الافتراضي ومساحة العمل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "سيتم إنشاء جميع ملفات الأكواد داخل هذا المسار لتبسيط إدارة التوجيهات البرمجية تلقائياً.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item {
            Text("تعديل البادئات والتوجيهات النشطة:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = builderPrefix,
                        onValueChange = { builderPrefix = it },
                        label = { Text("بادئة منشئ الملفات @builder") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = executorPrefix,
                        onValueChange = { executorPrefix = it },
                        label = { Text("بادئة منفذ الأوامر @executor") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = treedocPrefix,
                        onValueChange = { treedocPrefix = it },
                        label = { Text("بادئة مولد التقارير @treedoc") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text("إعدادات التكامل الأندرويدي المتقدم:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تشغيل تلقائي مع بدء النظام (Boot)", fontSize = 13.sp)
                        Switch(checked = autostart, onCheckedChange = { autostart = it })
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تفعيل الاختصار العائم الذهبي", fontSize = 13.sp)
                        Switch(
                            checked = bubbleEnabled,
                            onCheckedChange = {
                                bubbleEnabled = it
                                if (it) {
                                    if (!Settings.canDrawOverlays(context)) {
                                        Toast.makeText(context, "الرجاء منح صلاحية التراكب أولاً 🔐", Toast.LENGTH_LONG).show()
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        viewModel.toggleOverlayBubble(true, true)
                                    }
                                } else {
                                    viewModel.toggleOverlayBubble(false, true)
                                }
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الوضع الذهبي المُحاكى (إطار محاكي وبلاطات)", fontSize = 13.sp)
                        Switch(
                            checked = goldenMode,
                            onCheckedChange = { goldenMode = it },
                            modifier = Modifier.testTag("golden_mode_switch")
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text("استثناء تحسين استهلاك البطارية ⚡", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            builderPrefix = builderPrefix,
                            executorPrefix = executorPrefix,
                            treedocPrefix = treedocPrefix,
                            baseDir = bDir,
                            autostartEnabled = autostart,
                            bubbleEnabled = bubbleEnabled,
                            goldenMode = goldenMode
                        )
                    )
                    Toast.makeText(context, "تم حفظ الإعدادات بنجاح! 💾", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_settings_btn")
            ) {
                Text("حفظ التفضيلات والإعدادات 💾", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =====================================================================
// 6. شريط الحالة والمكونات المحاكية للوضع الذهبي (Simulated UX UI Components)
// =====================================================================

@Composable
fun SimulatedStatusBar(
    isMonitorActive: Boolean,
    isBubbleActive: Boolean,
    onStatusBarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFF151515))
            .clickable(onClick = onStatusBarClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("17:56", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            if (isMonitorActive) {
                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF66BB6A))
            }
            if (isBubbleActive) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFB300))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFB300))
            Spacer(modifier = Modifier.width(6.dp))
            Text("92%", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFFB300))
        }
    }
}

@Composable
fun SimulatedQuickTilesDrawer(
    isMonitorActive: Boolean,
    isBubbleActive: Boolean,
    onMonitorToggle: (Boolean) -> Unit,
    onBubbleToggle: (Boolean) -> Unit,
    onQuickReportClick: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onClose)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .clickable(enabled = false, onClick = {}) // منع إغلاق عند النقر بالداخل
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("لوحة البلاطات السريعة 👑", fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // بلاطة 1: مراقب الحافظة
                QuickTileButton(
                    title = "مراقب الحافظة",
                    isActive = isMonitorActive,
                    icon = Icons.Default.Radar,
                    onClick = { onMonitorToggle(!isMonitorActive) }
                )

                // بلاطة 2: فقاعة عائمة
                QuickTileButton(
                    title = "الكرة العائمة",
                    isActive = isBubbleActive,
                    icon = Icons.Default.Circle,
                    onClick = { onBubbleToggle(!isBubbleActive) }
                )

                // بلاطة 3: تقرير سريع (الذهبية!)
                QuickTileButton(
                    title = "تقرير سريع 📂",
                    isActive = true,
                    icon = Icons.Default.AccountTree,
                    onClick = onQuickReportClick,
                    isSpecialGold = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "اسحب لأعلى أو انقر بالخارج للإغلاق والتراجع.",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun QuickTileButton(
    title: String,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isSpecialGold: Boolean = false
) {
    val containerColor = when {
        isSpecialGold -> Color(0xFFFFB300)
        isActive -> Color(0xFFFFB300).copy(alpha = 0.8f)
        else -> Color(0xFF333333)
    }
    val contentColor = if (isSpecialGold || isActive) Color.Black else Color.White

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(title, fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
    }
}

// تم توحيد مساعدات الحجم المبنية في أندرويد كومبوز
