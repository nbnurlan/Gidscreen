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
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
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
import com.example.network.GeminiService
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
    // Feature 1: Floating Action Button
    // -------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBubble() {
        if (bubbleView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }
        bubbleParams = params

        val view = ComposeView(this).apply {
            bubbleLifecycleOwner.attachToView(this)
            setContent {
                MyApplicationTheme {
                    FloatingBubbleContent(
                        onClick = {
                            showSelectionOverlay()
                        }
                    )
                }
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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        selectionParams = params

        val view = ComposeView(this).apply {
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

        // Invalidate any previously cached frame so the capture gets a clean, fresh frame
        ScreenCaptureHelper.invalidateFrame()

        serviceScope.launch {
            // Delay to ensure the overlay layers have cleared from GPU compositing & SurfaceFlinger
            delay(150)

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
                boundingBox = screenBoundingBox
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

            // Immediately show floating chat window and trigger auto AI analysis
            analysisState.value = AnalysisState.Analyzing(croppedBitmap)
            chatMessages.clear()
            showFloatingChatDialog()

            // Automatically call Gemini 2.5 Flash without requiring manual text input
            val result = GeminiService.analyzeScreenCrop(croppedBitmap)

            result.onSuccess { explanation ->
                analysisState.value = AnalysisState.Success(explanation, croppedBitmap)
                chatMessages.add(
                    ChatMessage(
                        sender = MessageSender.AI,
                        text = explanation
                    )
                )
            }.onFailure { error ->
                analysisState.value = AnalysisState.Error(
                    message = error.message ?: getString(R.string.error_general_gemini),
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
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
        chatParams = params

        val view = ComposeView(this).apply {
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
                bitmap = currentBitmap
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
            val result = GeminiService.analyzeScreenCrop(bitmap)
            result.onSuccess { explanation ->
                analysisState.value = AnalysisState.Success(explanation, bitmap)
                chatMessages.clear()
                chatMessages.add(ChatMessage(sender = MessageSender.AI, text = explanation))
            }.onFailure { error ->
                analysisState.value = AnalysisState.Error(
                    message = error.message ?: "Analysis retry failed.",
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
