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
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.example.ui.CompactLanguageDropdown
import com.example.ui.FloatingChatDialogContent
import com.example.ui.LassoSelectionContent
import com.example.ui.MasterServiceCard
import com.example.ui.PermissionStatusItem
import com.example.ui.SettingsContent
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
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
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

    // Navigation state: Settings screen opened via gear icon at the top
    var showSettingsScreen by remember { mutableStateOf(false) }

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
                    navigationIcon = {
                        if (showSettingsScreen) {
                            IconButton(
                                onClick = { showSettingsScreen = false },
                                modifier = Modifier.testTag("settings_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.btn_back),
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    title = {
                        if (showSettingsScreen) {
                            Text(
                                text = stringResource(R.string.settings_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        } else {
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
                        }
                    },
                    actions = {
                        if (!showSettingsScreen) {
                            IconButton(
                                onClick = { showSettingsScreen = true },
                                modifier = Modifier.testTag("top_bar_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings_title),
                                    tint = Color.White.copy(alpha = 0.9f)
                                )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (showSettingsScreen) {
                    BackHandler(enabled = true) {
                        showSettingsScreen = false
                    }
                    // Settings Screen (Sozlamalar va Tilni o'zgartirish)
                    SettingsContent(
                        hasOverlayPermission = hasOverlayPermission,
                        hasCaptureToken = hasCaptureToken,
                        hasNotificationPermission = hasNotificationPermission,
                        onOpenOverlayPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        },
                        onOpenCapturePermission = {
                            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                        },
                        onOpenNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                } else {
                    // Control Center (Asosiy oyna)
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
