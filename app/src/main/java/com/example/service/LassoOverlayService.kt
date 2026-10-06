package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import com.example.ScreenCapturePermissionActivity
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.example.MainActivity
import com.example.R
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.network.ApiKeyInvalidException
import com.example.network.ApiKeyLeakedException
import com.example.network.GeminiService
import com.example.ui.FloatingBubbleContent
import com.example.ui.FloatingChatDialogContent
import com.example.ui.LassoSelectionContent
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val bubbleDocked = mutableStateOf(false)
    private val bubbleDockRight = mutableStateOf(false)
    private var bubbleIdleJob: Job? = null

    private fun scheduleBubbleDock() {
        bubbleIdleJob?.cancel()
        if (bubbleParams == null) return
        bubbleIdleJob = serviceScope.launch {
            delay(3000)
            val view = bubbleView ?: return@launch
            if (view.visibility != View.VISIBLE) return@launch
            val params = bubbleParams ?: return@launch
            val dm = resources.displayMetrics
            bubbleDockRight.value = params.x + view.width / 2 >= dm.widthPixels / 2
            bubbleDocked.value = true
            params.width = (20 * dm.density).toInt()
            params.x = if (bubbleDockRight.value) dm.widthPixels - params.width else 0
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun expandBubble() {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val dm = resources.displayMetrics
        params.width = (64 * dm.density).toInt()
        params.x = if (bubbleDockRight.value) (dm.widthPixels - params.width).coerceAtLeast(0) else 0
        bubbleDocked.value = false
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun restoreFloatingBubble() {
        bubbleView?.visibility = View.VISIBLE
        scheduleBubbleDock()
    }

    private var selectionView: ComposeView? = null
    private var selectionParams: WindowManager.LayoutParams? = null

    private var chatView: ComposeView? = null
    private var chatParams: WindowManager.LayoutParams? = null

    // State for Chat Dialog
    private val analysisState = mutableStateOf<AnalysisState>(AnalysisState.Idle)
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private var currentBitmap: Bitmap? = null

    private lateinit var sessionStore: ChatSessionStore
    private val windowState by lazy { getSharedPreferences("floating-chat-window", MODE_PRIVATE) }
    private var restoringSession = true
    private var openSelectionAfterRestore = false
    private var captureGeneration = 0
    private var foregroundReady = false

    private suspend fun persistSession() {
        val messages = chatMessages.toList()
        val pending = analysisState.value is AnalysisState.Analyzing ||
            analysisState.value is AnalysisState.Capturing
        try {
            sessionStore.save(messages, pending)
        } catch (error: java.io.IOException) {
            android.util.Log.e("Gidscreen", "Unable to save chat session", error)
            android.widget.Toast.makeText(this, R.string.session_save_failed,
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun saveChatWindow() {
        val cp = chatParams ?: return
        windowState.edit().putInt("x", cp.x).putInt("y", cp.y)
            .putInt("width", cp.width).putInt("height", cp.height).apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        bubbleLifecycleOwner.onCreate()
        selectionLifecycleOwner.onCreate()
        chatLifecycleOwner.onCreate()

        // A dead process cannot reuse MediaProjection consent. Wait for a fresh user start.
        if (!MediaProjectionHolder.isAvailable || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        try {
            startForegroundNotification()
            foregroundReady = true
        } catch (_: SecurityException) {
            MediaProjectionHolder.clear()
            stopSelf()
            return
        }
        sessionStore = ChatSessionStore(this)
        serviceScope.launch {
            try {
                val saved = sessionStore.load()
                chatMessages.addAll(saved.messages)
                currentBitmap = saved.messages.lastOrNull { it.image != null }?.image
                analysisState.value = if (saved.interrupted) {
                    AnalysisState.Error(getString(R.string.session_interrupted), currentBitmap)
                } else {
                    saved.messages.lastOrNull { it.sender == MessageSender.AI }?.let {
                        AnalysisState.Success(it.text, currentBitmap)
                    } ?: AnalysisState.Idle
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("Gidscreen", "Unable to restore chat", error)
                analysisState.value = AnalysisState.Error(getString(R.string.session_restore_failed), null)
            }
            restoringSession = false
            showFloatingBubble()
            if (windowState.getBoolean("open", false)) {
                bubbleView?.visibility = View.GONE
                showFloatingChatDialog()
            }
            if (openSelectionAfterRestore) {
                openSelectionAfterRestore = false
                showSelectionOverlay()
            }
        }

        // Warm up MediaProjection and VirtualDisplay session if available
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = MediaProjectionHolder.mediaProjection ?: MediaProjectionHolder.obtainMediaProjection(projectionManager)
        if (projection != null) {
            ScreenCaptureHelper.ensureSession(this, projection)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_SELECTION -> {
                if (restoringSession) openSelectionAfterRestore = true else showSelectionOverlay()
            }
        }

        // Ensure session is active whenever service is commanded
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = MediaProjectionHolder.mediaProjection ?: MediaProjectionHolder.obtainMediaProjection(projectionManager)
        if (projection != null) {
            ScreenCaptureHelper.ensureSession(this, projection)
        }

        return START_NOT_STICKY
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
    // Feature 1: Floating Action Button (Free Drag & Drop Anywhere & Single Tap)
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
            gravity = Gravity.TOP or Gravity.LEFT
            x = 36
            y = screenHeight / 3
        }
        bubbleParams = params

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val view = ComposeView(themedContext).apply {
            bubbleLifecycleOwner.attachToView(this)
            setContent {
                MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                    FloatingBubbleContent(docked = bubbleDocked.value, dockRight = bubbleDockRight.value)
                }
            }
        }

        // Touch & Drag listener: siljitishda tugma harakatlanadi, bir marta bosganda tanlash oynasi ochiladi
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var touchStartTime = 0L
        var startedDocked = false

        view.setOnTouchListener { v, event ->
            val currentParams = bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleIdleJob?.cancel()
                    startedDocked = bubbleDocked.value
                    if (startedDocked) expandBubble()
                    initialX = currentParams.x
                    initialY = currentParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val dm = resources.displayMetrics
                        val viewW = if (view.width > 0) view.width else 160
                        val viewH = if (view.height > 0) view.height else 160
                        val maxX = (dm.widthPixels - viewW).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - viewH).coerceAtLeast(0)

                        // Barmoq harakati bilan birga siljiydi
                        currentParams.x = (initialX + dx).coerceIn(0, maxX)
                        currentParams.y = (initialY + dy).coerceIn(0, maxY)

                        try {
                            windowManager.updateViewLayout(view, currentParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchStartTime
                    val dx = kotlin.math.abs((event.rawX - initialTouchX).toInt())
                    val dy = kotlin.math.abs((event.rawY - initialTouchY).toInt())

                    if (!isDragging && dx <= touchSlop && dy <= touchSlop && duration < 400) {
                        // Bir marta oddiy bosilganda (click) avvalgi amali (ekranni belgilash) chaqiriladi
                        v.performClick()
                        if (startedDocked) scheduleBubbleDock() else {
                            bubbleView?.visibility = View.GONE
                            showFloatingChatDialog()
                        }
                    } else {
                        // Siljitish tugagach qo'yilgan joyda saqlanadi
                        val dm = resources.displayMetrics
                        val viewW = if (view.width > 0) view.width else 160
                        val viewH = if (view.height > 0) view.height else 160
                        val maxX = (dm.widthPixels - viewW).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - viewH).coerceAtLeast(0)

                        currentParams.x = currentParams.x.coerceIn(0, maxX)
                        currentParams.y = currentParams.y.coerceIn(0, maxY)

                        try {
                            windowManager.updateViewLayout(view, currentParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    scheduleBubbleDock()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    scheduleBubbleDock()
                    true
                }
                else -> false
            }
        }

        bubbleLifecycleOwner.onStart()
        try {
            windowManager.addView(view, params)
            bubbleView = view
            scheduleBubbleDock()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideFloatingBubble() {
        bubbleIdleJob?.cancel()
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
        if (restoringSession || selectionView != null || analysisState.value is AnalysisState.Analyzing ||
            analysisState.value is AnalysisState.Capturing) return

        if (!MediaProjectionHolder.isAvailable) {
            // User-triggered consent, without destroying or replacing the chat view.
            startActivity(Intent(this, ScreenCapturePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(chatView?.windowToken, 0)
        chatView?.clearFocus()

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
                MyApplicationTheme(darkTheme = false, dynamicColor = false) {
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
            dismissSelectionOverlay(restoreBubble = true)
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
        if (restoreBubble) {
            if (chatView != null) {
                chatView?.visibility = View.VISIBLE
            } else {
                restoreFloatingBubble()
            }
        }
    }

    // -------------------------------------------------------------
    // Feature 3: Screen Capture & Auto AI Analysis
    // -------------------------------------------------------------
    private fun onLassoSelected(path: Path?, boundingBox: RectF) {
        if (analysisState.value is AnalysisState.Capturing ||
            analysisState.value is AnalysisState.Analyzing) return
        analysisState.value = AnalysisState.Capturing
        val generation = captureGeneration
        val rotation = windowManager.defaultDisplay.rotation
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

        serviceScope.launch {
            // Delay to ensure the overlay layers have cleared from GPU compositing & SurfaceFlinger
            delay(150)
            if (generation != captureGeneration) return@launch
            val captureStartTime = System.nanoTime()
            ScreenCaptureHelper.prepareForCapture(captureStartTime)

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

            if (generation != captureGeneration || rotation != windowManager.defaultDisplay.rotation) {
                croppedBitmap?.recycle()
                if (generation == captureGeneration) {
                    analysisState.value = AnalysisState.Idle
                    dismissSelectionOverlay()
                    showSelectionOverlay()
                }
                return@launch
            }

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
                image = croppedBitmap,
                isVisible = false // Hidden from chat UI: sent in background to Gemini
            )
            chatMessages.add(userMsg)
            persistSession()

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
            persistSession()
        }
    }

    // -------------------------------------------------------------
    // Feature 4: Resizable Floating Chat Window
    // -------------------------------------------------------------
    private fun showFloatingChatDialog() {
        windowState.edit().putBoolean("open", true).apply()
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
            width = windowState.getInt("width", defaultWidth).coerceIn(1, metrics.widthPixels)
            height = windowState.getInt("height", defaultHeight).coerceIn(1, metrics.heightPixels)
            x = windowState.getInt("x", 0).coerceIn(-(metrics.widthPixels - width) / 2, (metrics.widthPixels - width) / 2)
            y = windowState.getInt("y", 0).coerceIn(-(metrics.heightPixels - height) / 2, (metrics.heightPixels - height) / 2)
        }
        chatParams = params

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val view = ComposeView(themedContext).apply {
            chatLifecycleOwner.attachToView(this)
            setContent {
                MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                    FloatingChatDialogContent(
                        initialDraft = windowState.getString("draft", "") ?: "",
                        onDraftChanged = { windowState.edit().putString("draft", it).apply() },
                        analysisState = analysisState.value,
                        chatMessages = chatMessages,
                        onSendFollowUp = { question ->
                            handleFollowUp(question)
                        },
                        onNewSelectionRequested = {
                            showSelectionOverlay()
                        },
                        onRetry = {
                            val bmp = currentBitmap
                            if (bmp != null) {
                                retryAnalysis(bmp)
                            } else {
                                showSelectionOverlay()
                            }
                        },
                        onClose = {
                            windowState.edit().putBoolean("open", false).apply()
                            hideFloatingChatDialog()
                            restoreFloatingBubble()
                        },
                        onDragDelta = { dx, dy ->
                            val cp = chatParams ?: return@FloatingChatDialogContent
                            cp.x += dx.toInt()
                            cp.y += dy.toInt()
                            try {
                                windowManager.updateViewLayout(chatView, cp)
                                saveChatWindow()
                            } catch (_: Exception) {}
                        },
                        onResizeDelta = { dw, dh ->
                            val cp = chatParams ?: return@FloatingChatDialogContent
                            val newW = (cp.width + dw.toInt()).coerceIn(280.coerceAtMost(resources.displayMetrics.widthPixels), resources.displayMetrics.widthPixels)
                            val newH = (cp.height + dh.toInt()).coerceIn(240.coerceAtMost(resources.displayMetrics.heightPixels), resources.displayMetrics.heightPixels)
                            cp.width = newW
                            cp.height = newH
                            try {
                                windowManager.updateViewLayout(chatView, cp)
                                saveChatWindow()
                            } catch (_: Exception) {}
                        },
                        onClearChat = {
                            if (analysisState.value !is AnalysisState.Analyzing &&
                                analysisState.value !is AnalysisState.Capturing) {
                                chatMessages.clear()
                                currentBitmap = null
                                analysisState.value = AnalysisState.Idle
                                serviceScope.launch { persistSession() }
                            }
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
        val text = question.trim()
        if (text.isEmpty() || analysisState.value is AnalysisState.Analyzing ||
            analysisState.value is AnalysisState.Capturing) return

        // Reserve the request synchronously, before another tap can enqueue work.
        val previousState = analysisState.value
        val history = chatMessages.toList()
        analysisState.value = AnalysisState.Analyzing(currentBitmap)
        chatMessages.add(ChatMessage(sender = MessageSender.USER, text = text))

        serviceScope.launch {
            try {
                persistSession()
                val result = GeminiService.continueChat(
                    history = history,
                    newQuestion = text,
                    bitmap = null
                )
                result.onSuccess { answer ->
                    chatMessages.add(ChatMessage(sender = MessageSender.AI, text = answer))
                }.onFailure { error ->
                    chatMessages.add(ChatMessage(
                        sender = MessageSender.SYSTEM,
                        text = "Error: ${error.message}"
                    ))
                }
            } finally {
                analysisState.value = previousState
                persistSession()
            }
        }
    }

    private fun retryAnalysis(bitmap: Bitmap) {
        if (analysisState.value is AnalysisState.Analyzing) return
        analysisState.value = AnalysisState.Analyzing(bitmap)
        serviceScope.launch {
            persistSession()
            val requestIndex = chatMessages.indexOfLast { it.sender == MessageSender.USER }
            val request = chatMessages.getOrNull(requestIndex)
            val userPrompt = request?.text ?: GeminiService.getDefaultAnalysisPrompt()
            val result = GeminiService.continueChat(
                history = chatMessages.take(requestIndex.coerceAtLeast(0)),
                newQuestion = userPrompt,
                bitmap = if (request != null) request.image else bitmap
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
            persistSession()
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
        restoreFloatingBubble()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val wasSelecting = selectionView != null
        if (wasSelecting) {
            captureGeneration++
            if (analysisState.value is AnalysisState.Capturing) analysisState.value = AnalysisState.Idle
            dismissSelectionOverlay()
            // Old touch coordinates are invalid after rotation. Start with an empty selection.
            showSelectionOverlay()
        }
        chatParams?.let { cp ->
            val metrics = resources.displayMetrics
            cp.width = cp.width.coerceAtMost(metrics.widthPixels)
            cp.height = cp.height.coerceAtMost(metrics.heightPixels)
            cp.x = cp.x.coerceIn(-(metrics.widthPixels - cp.width) / 2, (metrics.widthPixels - cp.width) / 2)
            cp.y = cp.y.coerceIn(-(metrics.heightPixels - cp.height) / 2, (metrics.heightPixels - cp.height) / 2)
            chatView?.let { runCatching { windowManager.updateViewLayout(it, cp) } }
            saveChatWindow()
        }
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        bubbleIdleJob?.cancel()
        if (bubbleDocked.value) expandBubble()
        val dm = resources.displayMetrics
        params.x = params.x.coerceIn(0, (dm.widthPixels - (64 * dm.density).toInt()).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (dm.heightPixels - (64 * dm.density).toInt()).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
        scheduleBubbleDock()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        hideFloatingBubble()
        dismissSelectionOverlay(restoreBubble = false)
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
