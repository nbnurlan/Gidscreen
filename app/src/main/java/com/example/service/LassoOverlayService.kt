package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.example.LassoApplication
import com.example.MainActivity
import com.example.R
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.network.ApiKeyInvalidException
import com.example.network.ApiKeyLeakedException
import com.example.network.GeminiService
import com.example.ui.DockSide
import com.example.ui.FloatingBubbleContent
import com.example.ui.FloatingChatDialogContent
import com.example.ui.LassoSelectionContent
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LassoOverlayService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager

    // Lifecycle owners for ComposeViews
    private val bubbleLifecycleOwner = OverlayLifecycleOwner()
    private val selectionLifecycleOwner = OverlayLifecycleOwner()
    private val chatLifecycleOwner = OverlayLifecycleOwner()

    // Overlay Views
    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val isBubbleTuckedState = mutableStateOf(false)
    private val bubbleDockSideState = mutableStateOf(DockSide.LEFT)

    private var selectionView: ComposeView? = null
    private var selectionParams: WindowManager.LayoutParams? = null

    private var chatView: ComposeView? = null
    private var chatParams: WindowManager.LayoutParams? = null

    // State for Chat Dialog
    private val analysisState = mutableStateOf<AnalysisState>(AnalysisState.Idle)
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private var currentBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        bubbleLifecycleOwner.onCreate()
        selectionLifecycleOwner.onCreate()
        chatLifecycleOwner.onCreate()

        startForegroundNotification()
        showFloatingBubble()

        // Warm up MediaProjection and VirtualDisplay session if available
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = MediaProjectionHolder.mediaProjection ?: MediaProjectionHolder.obtainMediaProjection(projectionManager)
        if (projection != null) {
            ScreenCaptureHelper.ensureSession(this, projection)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_SELECTION -> {
                showSelectionOverlay()
            }
        }

        // Ensure session is active whenever service is commanded
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = MediaProjectionHolder.mediaProjection ?: MediaProjectionHolder.obtainMediaProjection(projectionManager)
        if (projection != null) {
            ScreenCaptureHelper.ensureSession(this, projection)
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LassoOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, LassoApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, getString(R.string.btn_stop_service), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                LassoApplication.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(LassoApplication.NOTIFICATION_ID, notification)
        }
    }

    // -------------------------------------------------------------
    // Feature 1: Floating Action Button (Movable Anywhere & Edge-Tuckable)
    // -------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBubble() {
        if (bubbleView != null) return

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = screenHeight / 3
        }
        bubbleParams = params

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val view = ComposeView(themedContext).apply {
            bubbleLifecycleOwner.attachToView(this)
            setContent {
                MyApplicationTheme {
                    val isTucked by isBubbleTuckedState
                    val dockSide by bubbleDockSideState
                    FloatingBubbleContent(
                        isTucked = isTucked,
                        dockSide = dockSide,
                        onClick = {
                            showSelectionOverlay()
                        },
                        onTuckClick = {
                            tuckBubbleToEdge()
                        },
                        onUntuckClick = {
                            untuckBubbleFromEdge()
                        }
                    )
                }
            }
        }

        // Touch & Drag listener for free placement anywhere on screen & edge docking
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { _, event ->
            val currentParams = bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = currentParams.x
                    initialY = currentParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble())

                    if (!isDragging && distance > touchSlop) {
                        isDragging = true
                        // When user pulls the edge tab or drags bubble, untuck so it floats with finger
                        if (isBubbleTuckedState.value) {
                            isBubbleTuckedState.value = false
                        }
                    }

                    if (isDragging) {
                        val dm = resources.displayMetrics
                        val maxCoordX = (dm.widthPixels - 60).coerceAtLeast(0)
                        val maxCoordY = (dm.heightPixels - 120).coerceAtLeast(0)

                        currentParams.x = (initialX + dx).coerceIn(0, maxCoordX)
                        currentParams.y = (initialY + dy).coerceIn(40, maxCoordY)

                        bubbleDockSideState.value = if (currentParams.x < dm.widthPixels / 2) {
                            DockSide.LEFT
                        } else {
                            DockSide.RIGHT
                        }

                        try {
                            windowManager.updateViewLayout(view, currentParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Clean tap without drag: launch screen selection overlay
                        showSelectionOverlay()
                    } else {
                        // Drag completed: check if placed near screen edge to tuck
                        val dm = resources.displayMetrics
                        val edgeThreshold = 130
                        val isNearLeft = currentParams.x < edgeThreshold
                        val isNearRight = currentParams.x > (dm.widthPixels - edgeThreshold - 80)

                        if (isNearLeft) {
                            bubbleDockSideState.value = DockSide.LEFT
                            isBubbleTuckedState.value = true
                            currentParams.x = 0
                        } else if (isNearRight) {
                            bubbleDockSideState.value = DockSide.RIGHT
                            isBubbleTuckedState.value = true
                            currentParams.x = dm.widthPixels - 48
                        } else {
                            // Freely positioned anywhere on screen
                            isBubbleTuckedState.value = false
                        }

                        try {
                            windowManager.updateViewLayout(view, currentParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        bubbleLifecycleOwner.onStart()
        try {
            windowManager.addView(view, params)
            bubbleView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tuckBubbleToEdge() {
        val currentParams = bubbleParams ?: return
        val currentView = bubbleView ?: return
        val dm = resources.displayMetrics
        val side = if (currentParams.x < dm.widthPixels / 2) DockSide.LEFT else DockSide.RIGHT
        bubbleDockSideState.value = side
        isBubbleTuckedState.value = true
        currentParams.x = if (side == DockSide.LEFT) 0 else (dm.widthPixels - 48)
        try {
            windowManager.updateViewLayout(currentView, currentParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun untuckBubbleFromEdge() {
        val currentParams = bubbleParams ?: return
        val currentView = bubbleView ?: return
        val dm = resources.displayMetrics
        isBubbleTuckedState.value = false
        currentParams.x = if (bubbleDockSideState.value == DockSide.LEFT) 36 else (dm.widthPixels - 180)
        try {
            windowManager.updateViewLayout(currentView, currentParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideFloatingBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bubbleView = null
        }
    }

    // -------------------------------------------------------------
    // Feature 2: Fullscreen Custom Freehand/Lasso Screen Selection
    // -------------------------------------------------------------
    private fun showSelectionOverlay() {
        if (selectionView != null) return

        // Hide floating bubble while selecting
        bubbleView?.visibility = View.GONE
        chatView?.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        selectionParams = params

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val view = ComposeView(themedContext).apply {
            selectionLifecycleOwner.attachToView(this)
            setContent {
                MyApplicationTheme {
                    LassoSelectionContent(
                        onSelectionConfirmed = { path, bounds ->
                            onLassoSelected(path, bounds)
                        },
                        onDismiss = {
                            dismissSelectionOverlay(restoreBubble = true)
                        }
                    )
                }
            }
        }

        selectionLifecycleOwner.onStart()
        try {
            windowManager.addView(view, params)
            selectionView = view
        } catch (e: Exception) {
            e.printStackTrace()
            bubbleView?.visibility = View.VISIBLE
        }
    }

    private fun dismissSelectionOverlay(restoreBubble: Boolean = true) {
        selectionView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectionView = null
        }
        if (restoreBubble && chatView == null) {
            bubbleView?.visibility = View.VISIBLE
        }
    }

    // -------------------------------------------------------------
    // Feature 3: Screen Capture & Auto AI Analysis
    // -------------------------------------------------------------
    private fun onLassoSelected(path: Path?, boundingBox: RectF) {
        // 1. Calculate system offsets and screen coordinates BEFORE modifying view visibility
        val location = IntArray(2)
        selectionView?.getLocationOnScreen(location)
        var offsetX = location[0].toFloat()
        var offsetY = location[1].toFloat()

        // Account for any system insets (status bar, display cutout) if window was not placed at screen origin
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rootInsets = selectionView?.rootWindowInsets
            val statusBars = rootInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            if (offsetY == 0f && statusBars != null && statusBars.top > 0) {
                val isNoLimits = (selectionParams?.flags ?: 0) and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0
                if (!isNoLimits) {
                    offsetY += statusBars.top
                }
            }
        }

        val screenBoundingBox = RectF(
            boundingBox.left + offsetX,
            boundingBox.top + offsetY,
            boundingBox.right + offsetX,
            boundingBox.bottom + offsetY
        )

        val screenPath = if (path != null && !path.isEmpty) {
            Path(path).apply {
                offset(offsetX, offsetY)
            }
        } else null

        // 2. Temporarily hide ALL overlay layers (dim mask, drawing paths, and floating widgets)
        // so they do not block the underlying screen text/content in the capture!
        selectionView?.visibility = View.INVISIBLE
        bubbleView?.visibility = View.GONE
        chatView?.visibility = View.GONE

        val captureStartTime = System.currentTimeMillis()
        ScreenCaptureHelper.prepareForCapture(captureStartTime)

        serviceScope.launch {
            // Delay to ensure the overlay layers have cleared from GPU compositing & SurfaceFlinger
            delay(100)

            var projection = MediaProjectionHolder.mediaProjection

            if (projection == null) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = MediaProjectionHolder.obtainMediaProjection(projectionManager)
            }

            if (projection == null) {
                dismissSelectionOverlay(restoreBubble = false)
                analysisState.value = AnalysisState.Error(
                    message = getString(R.string.error_no_projection),
                    thumbnail = null
                )
                showFloatingChatDialog()
                return@launch
            }

            analysisState.value = AnalysisState.Capturing

            val croppedBitmap = ScreenCaptureHelper.captureArea(
                context = this@LassoOverlayService,
                mediaProjection = projection,
                selectionPath = screenPath,
                boundingBox = screenBoundingBox,
                minTimestamp = captureStartTime
            )

            // Remove selection overlay completely now that capture is done
            dismissSelectionOverlay(restoreBubble = false)

            currentBitmap = croppedBitmap

            if (croppedBitmap == null) {
                val errorMsg = if (MediaProjectionHolder.mediaProjection == null) {
                    getString(R.string.error_no_projection)
                } else {
                    getString(R.string.error_analyze_failed)
                }
                analysisState.value = AnalysisState.Error(
                    message = errorMsg,
                    thumbnail = null
                )
                showFloatingChatDialog()
                return@launch
            }

            // Immediately show floating chat window and trigger multi-turn AI analysis
            analysisState.value = AnalysisState.Analyzing(croppedBitmap)
            showFloatingChatDialog()

            val captureNumber = chatMessages.count { it.image != null } + 1
            val currentLang = LocaleHelper.currentLanguage.value
            val userCapturePrompt = if (chatMessages.isEmpty()) {
                GeminiService.getDefaultAnalysisPrompt()
            } else {
                when (currentLang) {
                    LocaleHelper.LANG_RU -> "Фрагмент №$captureNumber: Проанализируйте новый выделенный фрагмент экрана в контексте нашего диалога."
                    LocaleHelper.LANG_EN -> "Capture #$captureNumber: Analyze this new screen selection in the context of our ongoing conversation."
                    else -> "№$captureNumber tanlov: Ushbu yangi belgilangan ekran qismini davom etayotgan suhbatimiz kontekstida tahlil qiling."
                }
            }

            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                text = userCapturePrompt,
                image = croppedBitmap
            )
            chatMessages.add(userMsg)

            // Continue persistent multi-turn conversation with all previous context + new capture
            val result = GeminiService.continueChat(
                history = chatMessages.dropLast(1),
                newQuestion = userCapturePrompt,
                bitmap = croppedBitmap
            )

            result.onSuccess { explanation ->
                analysisState.value = AnalysisState.Success(explanation, croppedBitmap)
                chatMessages.add(
                    ChatMessage(
                        sender = MessageSender.AI,
                        text = explanation
                    )
                )
            }.onFailure { error ->
                val localizedMessage = when (error) {
                    is ApiKeyLeakedException -> getString(R.string.error_api_key_leaked)
                    is ApiKeyInvalidException -> getString(R.string.error_api_key_invalid)
                    else -> error.message ?: getString(R.string.error_general_gemini)
                }
                analysisState.value = AnalysisState.Error(
                    message = localizedMessage,
                    thumbnail = croppedBitmap
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Feature 4: Resizable Floating Chat Window
    // -------------------------------------------------------------
    private fun showFloatingChatDialog() {
        if (chatView != null) {
            chatView?.visibility = View.VISIBLE
            return
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val defaultWidth = (metrics.widthPixels * 0.88f).toInt().coerceIn(300, 900)
        val defaultHeight = (metrics.heightPixels * 0.60f).toInt().coerceIn(400, 1400)

        val params = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        chatParams = params

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val view = ComposeView(themedContext).apply {
            chatLifecycleOwner.attachToView(this)
            setContent {
                MyApplicationTheme {
                    FloatingChatDialogContent(
                        analysisState = analysisState.value,
                        chatMessages = chatMessages,
                        onSendFollowUp = { question ->
                            handleFollowUp(question)
                        },
                        onNewSelectionRequested = {
                            chatView?.visibility = View.GONE
                            showSelectionOverlay()
                        },
                        onRetry = {
                            val bmp = currentBitmap
                            if (bmp != null) {
                                retryAnalysis(bmp)
                            } else {
                                hideFloatingChatDialog()
                                showSelectionOverlay()
                            }
                        },
                        onClose = {
                            hideFloatingChatDialog()
                            bubbleView?.visibility = View.VISIBLE
                        },
                        onDragDelta = { dx, dy ->
                            val cp = chatParams ?: return@FloatingChatDialogContent
                            cp.x += dx.toInt()
                            cp.y += dy.toInt()
                            try {
                                windowManager.updateViewLayout(chatView, cp)
                            } catch (_: Exception) {}
                        },
                        onResizeDelta = { dw, dh ->
                            val cp = chatParams ?: return@FloatingChatDialogContent
                            val newW = (cp.width + dw.toInt()).coerceIn(280, metrics.widthPixels - 40)
                            val newH = (cp.height + dh.toInt()).coerceIn(240, metrics.heightPixels - 80)
                            cp.width = newW
                            cp.height = newH
                            try {
                                windowManager.updateViewLayout(chatView, cp)
                            } catch (_: Exception) {}
                        },
                        onClearChat = {
                            chatMessages.clear()
                            currentBitmap = null
                            analysisState.value = AnalysisState.Idle
                        }
                    )
                }
            }
        }

        chatLifecycleOwner.onStart()
        try {
            windowManager.addView(view, params)
            chatView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleFollowUp(question: String) {
        chatMessages.add(
            ChatMessage(sender = MessageSender.USER, text = question)
        )

        serviceScope.launch {
            val result = GeminiService.continueChat(
                history = chatMessages.dropLast(1),
                newQuestion = question,
                bitmap = null
            )
            result.onSuccess { answer ->
                chatMessages.add(
                    ChatMessage(sender = MessageSender.AI, text = answer)
                )
            }.onFailure { error ->
                chatMessages.add(
                    ChatMessage(
                        sender = MessageSender.SYSTEM,
                        text = "Error: ${error.message}"
                    )
                )
            }
        }
    }

    private fun retryAnalysis(bitmap: Bitmap) {
        analysisState.value = AnalysisState.Analyzing(bitmap)
        serviceScope.launch {
            val userPrompt = chatMessages.lastOrNull { it.sender == MessageSender.USER }?.text
                ?: GeminiService.getDefaultAnalysisPrompt()
            val result = GeminiService.continueChat(
                history = chatMessages.filter { it.sender != MessageSender.SYSTEM }.dropLast(1),
                newQuestion = userPrompt,
                bitmap = bitmap
            )
            result.onSuccess { explanation ->
                analysisState.value = AnalysisState.Success(explanation, bitmap)
                chatMessages.add(ChatMessage(sender = MessageSender.AI, text = explanation))
            }.onFailure { error ->
                val localizedMessage = when (error) {
                    is ApiKeyLeakedException -> getString(R.string.error_api_key_leaked)
                    is ApiKeyInvalidException -> getString(R.string.error_api_key_invalid)
                    else -> error.message ?: getString(R.string.error_general_gemini)
                }
                analysisState.value = AnalysisState.Error(
                    message = localizedMessage,
                    thumbnail = bitmap
                )
            }
        }
    }

    private fun hideFloatingChatDialog() {
        chatView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            chatView = null
        }
        chatLifecycleOwner.onStop()
        bubbleView?.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        hideFloatingBubble()
        dismissSelectionOverlay()
        hideFloatingChatDialog()

        bubbleLifecycleOwner.onDestroy()
        selectionLifecycleOwner.onDestroy()
        chatLifecycleOwner.onDestroy()

        ScreenCaptureHelper.release()
        MediaProjectionHolder.clear()
        _isRunning.value = false
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP"
        const val ACTION_OPEN_SELECTION = "com.example.service.ACTION_OPEN_SELECTION"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
