package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.network.GeminiService
import com.example.service.LassoOverlayService
import com.example.service.MediaProjectionHolder
import com.example.ui.FloatingChatDialogContent
import com.example.ui.GeminiApiStatusCard
import com.example.ui.LanguageSelectorCard
import com.example.ui.LassoSelectionContent
import com.example.ui.MasterServiceCard
import com.example.ui.PermissionStatusItem
import com.example.ui.QuickGuideCard
import com.example.ui.SampleContentCard
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocaleHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REQUEST_CAPTURE = "extra_request_capture"
    }

    private val requestCaptureTrigger = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra(EXTRA_REQUEST_CAPTURE, false) == true) {
            requestCaptureTrigger.value = true
        }

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    requestCaptureInitially = requestCaptureTrigger.value,
                    onRequestCaptureHandled = { requestCaptureTrigger.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_CAPTURE, false)) {
            requestCaptureTrigger.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    requestCaptureInitially: Boolean = false,
    onRequestCaptureHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentLang by LocaleHelper.currentLanguage.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val isServiceActive by LassoOverlayService.isRunning.collectAsState()
    val isProjectionActive by MediaProjectionHolder.isProjectionActive.collectAsState()
    var hasCaptureToken by remember { mutableStateOf(MediaProjectionHolder.isAvailable) }

    LaunchedEffect(isProjectionActive) {
        hasCaptureToken = isProjectionActive || MediaProjectionHolder.isAvailable
    }

    // In-app interactive test canvas state
    var showInAppLasso by remember { mutableStateOf(false) }
    var showInAppChatDialog by remember { mutableStateOf(false) }
    var inAppAnalysisState by remember { mutableStateOf<AnalysisState>(AnalysisState.Idle) }
    val inAppChatMessages = remember { mutableStateListOf<ChatMessage>() }
    var inAppThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    // Tab state (Control Center vs Test Sandbox)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTopBarLanguageMenu by remember { mutableStateOf(false) }

    val overlayRequiredMsg = stringResource(R.string.toast_overlay_required)
    val serviceStartedMsg = stringResource(R.string.toast_service_started)
    val serviceStoppedMsg = stringResource(R.string.toast_service_stopped)
    val captureDeclinedMsg = stringResource(R.string.toast_capture_declined)

    // Overlay Permission Launcher
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (!hasOverlayPermission) {
            Toast.makeText(context, overlayRequiredMsg, Toast.LENGTH_LONG).show()
        }
    }

    // MediaProjection Capture Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.resultCode = result.resultCode
            MediaProjectionHolder.resultData = result.data
            hasCaptureToken = true

            // Start foreground service now that token is secured
            startLassoService(context)
            Toast.makeText(context, serviceStartedMsg, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, captureDeclinedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    // Notification Permission Launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasCaptureToken = MediaProjectionHolder.isAvailable
    }

    LaunchedEffect(requestCaptureInitially) {
        if (requestCaptureInitially) {
            onRequestCaptureHandled()
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyanGlow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = stringResource(R.string.app_subtitle),
                                    fontSize = 11.sp,
                                    color = CyanGlow
                                )
                            }
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showTopBarLanguageMenu = true },
                                modifier = Modifier.testTag("top_bar_language_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = stringResource(R.string.btn_language),
                                    tint = CyanGlow
                                )
                            }

                            DropdownMenu(
                                expanded = showTopBarLanguageMenu,
                                onDismissRequest = { showTopBarLanguageMenu = false },
                                modifier = Modifier
                                    .background(DarkSurface)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            ) {
                                LocaleHelper.supportedLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(text = lang.flag, fontSize = 16.sp)
                                                Text(
                                                    text = lang.nativeName,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (currentLang == lang.code) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (currentLang == lang.code) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = CyanGlow,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            showTopBarLanguageMenu = false
                                            LocaleHelper.setLanguage(context, lang.code)
                                            val toastMsg = context.getString(R.string.toast_language_changed, lang.nativeName)
                                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkSurface,
                        titleContentColor = Color.White
                    ),
                    modifier = Modifier.shadow(4.dp)
                )
            },
            containerColor = Color(0xFF070B14)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Navigation
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = CyanGlow
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_control_center), fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.tab_interactive_sandbox), fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // Control Center
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Master Service Card
                        item {
                            MasterServiceCard(
                                isServiceActive = isServiceActive,
                                hasOverlayPermission = hasOverlayPermission,
                                hasCaptureToken = hasCaptureToken,
                                onToggleService = { activate ->
                                    if (activate) {
                                        if (!hasOverlayPermission) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            overlayPermissionLauncher.launch(intent)
                                            return@MasterServiceCard
                                        }

                                        // Prompt MediaProjection
                                        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                                    } else {
                                        stopLassoService(context)
                                        MediaProjectionHolder.clear()
                                        hasCaptureToken = false
                                        Toast.makeText(context, serviceStoppedMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        // In-App Language Selector Card
                        item {
                            LanguageSelectorCard(
                                currentLanguage = currentLang,
                                onLanguageSelected = { lang ->
                                    LocaleHelper.setLanguage(context, lang.code)
                                    val msg = context.getString(R.string.toast_language_changed, lang.nativeName)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // Permissions Status Section
                        item {
                            Text(
                                text = stringResource(R.string.section_permissions_title),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        // Overlay Permission Item
                        item {
                            PermissionStatusItem(
                                title = stringResource(R.string.perm_overlay_title),
                                subtitle = stringResource(R.string.perm_overlay_desc),
                                isGranted = hasOverlayPermission,
                                onGrant = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayPermissionLauncher.launch(intent)
                                }
                            )
                        }

                        // Screen Capture (MediaProjection API) Item
                        item {
                            PermissionStatusItem(
                                title = stringResource(R.string.perm_capture_title),
                                subtitle = stringResource(R.string.perm_capture_desc),
                                isGranted = hasCaptureToken,
                                onGrant = {
                                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                                }
                            )
                        }

                        // Notification Permission Item (Android 13+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            item {
                                PermissionStatusItem(
                                    title = stringResource(R.string.perm_notification_title),
                                    subtitle = stringResource(R.string.perm_notification_desc),
                                    isGranted = hasNotificationPermission,
                                    onGrant = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                )
                            }
                        }

                        // Gemini API Status Item
                        item {
                            GeminiApiStatusCard()
                        }

                        // Quick Guide Card
                        item {
                            QuickGuideCard()
                        }
                    }
                } else {
                    // Interactive Sandbox Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.sandbox_title),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.sandbox_desc),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }

                        // Sample Item 1: Code snippet
                        item {
                            SampleContentCard(
                                title = stringResource(R.string.sandbox_sample1_title),
                                icon = Icons.Default.Code,
                                content = "def binary_search(arr, target):\n    low = 0\n    high = len(arr) # Bug: should be len(arr) - 1\n    while low <= high:\n        mid = (low + high) // 2\n        if arr[mid] == target:\n            return mid\n        elif arr[mid] < target:\n            low = mid + 1\n        else:\n            high = mid - 1\n    return -1",
                                onTestLasso = {
                                    showInAppLasso = true
                                }
                            )
                        }

                        // Sample Item 2: Math / Physics
                        item {
                            SampleContentCard(
                                title = stringResource(R.string.sandbox_sample2_title),
                                icon = Icons.Default.Functions,
                                content = "Find the eigenvalues for the Hermitian matrix:\nH = [ 2   -i ]\n    [  i   2 ]\n\nCharacteristic equation: det(H - λI) = 0\n(2 - λ)^2 - (-i)(i) = (2 - λ)^2 - 1 = 0\nλ1 = 3, λ2 = 1",
                                onTestLasso = {
                                    showInAppLasso = true
                                }
                            )
                        }

                        // Test Action Buttons
                        item {
                            Button(
                                onClick = { showInAppLasso = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("test_lasso_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropFree,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.btn_launch_selector),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // In-App Selection Overlay
        AnimatedVisibility(
            visible = showInAppLasso,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LassoSelectionContent(
                onSelectionConfirmed = { _, bounds ->
                    showInAppLasso = false

                    // Generate test cropped bitmap from the simulated screen
                    val testBitmap = createSampleBitmap(bounds)
                    inAppThumbnail = testBitmap
                    inAppAnalysisState = AnalysisState.Analyzing(testBitmap)
                    inAppChatMessages.clear()
                    showInAppChatDialog = true

                    coroutineScope.launch {
                        val result = GeminiService.analyzeScreenCrop(testBitmap)
                        result.onSuccess { explanation ->
                            inAppAnalysisState = AnalysisState.Success(explanation, testBitmap)
                            inAppChatMessages.add(
                                ChatMessage(sender = MessageSender.AI, text = explanation)
                            )
                        }.onFailure { error ->
                            inAppAnalysisState = AnalysisState.Error(
                                message = error.message ?: "Failed to analyze selection.",
                                thumbnail = testBitmap
                            )
                        }
                    }
                },
                onDismiss = {
                    showInAppLasso = false
                }
            )
        }

        // In-App Resizable Floating Chat Window (Preview & Test)
        AnimatedVisibility(
            visible = showInAppChatDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                var windowWidth by remember { mutableStateOf(340.dp) }
                var windowHeight by remember { mutableStateOf(480.dp) }

                FloatingChatDialogContent(
                    analysisState = inAppAnalysisState,
                    chatMessages = inAppChatMessages,
                    onSendFollowUp = { question ->
                        inAppChatMessages.add(ChatMessage(sender = MessageSender.USER, text = question))
                        coroutineScope.launch {
                            val res = GeminiService.continueChat(
                                inAppChatMessages.dropLast(1),
                                question,
                                inAppThumbnail
                            )
                            res.onSuccess { ans ->
                                inAppChatMessages.add(ChatMessage(sender = MessageSender.AI, text = ans))
                            }.onFailure { err ->
                                inAppChatMessages.add(
                                    ChatMessage(sender = MessageSender.SYSTEM, text = "Error: ${err.message}")
                                )
                            }
                        }
                    },
                    onNewSelectionRequested = {
                        showInAppChatDialog = false
                        showInAppLasso = true
                    },
                    onRetry = {
                        inAppThumbnail?.let { bmp ->
                            inAppAnalysisState = AnalysisState.Analyzing(bmp)
                            coroutineScope.launch {
                                val res = GeminiService.analyzeScreenCrop(bmp)
                                res.onSuccess { exp ->
                                    inAppAnalysisState = AnalysisState.Success(exp, bmp)
                                    inAppChatMessages.clear()
                                    inAppChatMessages.add(ChatMessage(sender = MessageSender.AI, text = exp))
                                }
                            }
                        }
                    },
                    onClose = {
                        showInAppChatDialog = false
                    },
                    onDragDelta = { _, _ -> /* Centered dialog in preview mode */ },
                    onResizeDelta = { dw, dh ->
                        windowWidth = (windowWidth + (dw / 3).dp).coerceIn(280.dp, 390.dp)
                        windowHeight = (windowHeight + (dh / 3).dp).coerceIn(320.dp, 600.dp)
                    },
                    modifier = Modifier
                        .width(windowWidth)
                        .height(windowHeight)
                )
            }
        }
    }
}

private fun startLassoService(context: Context) {
    val intent = Intent(context, LassoOverlayService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopLassoService(context: Context) {
    val intent = Intent(context, LassoOverlayService::class.java).apply {
        action = LassoOverlayService.ACTION_STOP_SERVICE
    }
    context.startService(intent)
}

private fun createSampleBitmap(bounds: RectF): Bitmap {
    val width = bounds.width().toInt().coerceIn(200, 800)
    val height = bounds.height().toInt().coerceIn(150, 600)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint().apply { color = AndroidColor.rgb(15, 23, 42) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val textPaint = Paint().apply {
        color = AndroidColor.WHITE
        textSize = 28f
        isAntiAlias = true
    }
    canvas.drawText("Selected Screen Snippet", 20f, 50f, textPaint)

    val codePaint = Paint().apply {
        color = AndroidColor.rgb(56, 189, 248)
        textSize = 22f
        isAntiAlias = true
    }
    canvas.drawText("binary_search(arr, target)", 20f, 90f, codePaint)
    canvas.drawText("high = len(arr)  # Bug identified", 20f, 120f, codePaint)

    return bitmap
}
