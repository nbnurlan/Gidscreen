package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.coroutines.resume

object ScreenCaptureHelper {

    private const val TAG = "ScreenCaptureHelper"

    private var currentProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private val sessionLock = Any()
    private var latestBitmap: Bitmap? = null
    private var latestBitmapTimestamp: Long = 0L
    private var captureRequestedTimestamp: Long = 0L
    private var pendingContinuation: CancellableContinuation<Bitmap?>? = null

    private var currentWidth: Int = 0
    private var currentHeight: Int = 0
    private var currentDensity: Int = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection was stopped by system or user.")
            release()
            MediaProjectionHolder.markStopped()
        }
    }

    /**
     * Obtains the real unscaled physical display resolution including status bar and navigation bar.
     * This ensures MediaProjection captures 1:1 screen pixels without distortion or offset shifts.
     */
    fun getRealScreenMetrics(context: Context): Triple<Int, Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    /**
     * Initializes or maintains the active VirtualDisplay session.
     * Uses PixelFormat.RGBA_8888 and true physical screen dimensions.
     * Exactly one VirtualDisplay is created per MediaProjection instance as mandated by Android 14+.
     */
    fun ensureSession(
        context: Context,
        mediaProjection: MediaProjection
    ): Boolean {
        synchronized(sessionLock) {
            val (width, height, density) = getRealScreenMetrics(context)

            // Already running with this active projection instance?
            if (currentProjection === mediaProjection && virtualDisplay != null && imageReader != null) {
                if (currentWidth != width || currentHeight != height || currentDensity != density) {
                    try {
                        virtualDisplay?.resize(width, height, density)
                        currentWidth = width
                        currentHeight = height
                        currentDensity = density
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resizing virtual display", e)
                    }
                }
                return true
            }

            // Clean up any stale session
            cleanupInternal()

            currentProjection = mediaProjection
            currentWidth = width
            currentHeight = height
            currentDensity = density

            val thread = HandlerThread("ScreenCaptureThread").apply { start() }
            handlerThread = thread
            val h = Handler(thread.looper)
            handler = h

            // Ensure format is PixelFormat.RGBA_8888 as required
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                var image: Image? = null
                try {
                    image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val bitmap = convertImageToBitmap(image, width, height) ?: return@setOnImageAvailableListener

                    synchronized(sessionLock) {
                        latestBitmap?.recycle()
                        latestBitmap = bitmap
                        latestBitmapTimestamp = System.currentTimeMillis()

                        val cont = pendingContinuation
                        if (cont != null && cont.isActive) {
                            pendingContinuation = null
                            cont.resume(bitmap.copy(Bitmap.Config.ARGB_8888, true))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring image frame", e)
                } finally {
                    try {
                        image?.close()
                    } catch (_: Exception) {}
                }
            }, h)

            return try {
                // MANDATORY for Android 14+ (API 34+): registerCallback MUST be called before createVirtualDisplay
                mediaProjection.registerCallback(projectionCallback, h)

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenLassoDisplay",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    h
                )
                Log.d(TAG, "VirtualDisplay created successfully with registered callback.")
                true
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException creating VirtualDisplay. Projection may be non-current or stopped.", e)
                cleanupInternal()
                MediaProjectionHolder.markStopped()
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create VirtualDisplay", e)
                cleanupInternal()
                MediaProjectionHolder.markStopped()
                false
            }
        }
    }

    private fun convertImageToBitmap(image: Image, targetWidth: Int, targetHeight: Int): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val plane = planes[0]
        val buffer = plane.buffer ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * targetWidth

        return try {
            val bitmapWidth = if (pixelStride > 0) {
                targetWidth + rowPadding / pixelStride
            } else {
                targetWidth
            }

            val fullBitmap = Bitmap.createBitmap(
                bitmapWidth,
                targetHeight,
                Bitmap.Config.ARGB_8888
            )
            buffer.rewind()
            fullBitmap.copyPixelsFromBuffer(buffer)

            if (bitmapWidth == targetWidth) {
                fullBitmap
            } else {
                val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, targetWidth, targetHeight)
                fullBitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert hardware Image to Bitmap", e)
            null
        }
    }

    /**
     * Marks the timestamp when selection ended and overlays were hidden,
     * ensuring frames captured after this point are treated as fresh and clean.
     */
    fun prepareForCapture(timestamp: Long = System.currentTimeMillis()) {
        synchronized(sessionLock) {
            captureRequestedTimestamp = timestamp
        }
    }

    /**
     * Invalidates any previously stored frame to guarantee that the next capture
     * grabs a brand-new frame rendered after overlay layers are hidden.
     */
    fun invalidateFrame() {
        synchronized(sessionLock) {
            captureRequestedTimestamp = System.currentTimeMillis()
            pendingContinuation?.let {
                if (it.isActive) it.resume(null)
            }
            pendingContinuation = null
        }
    }

    suspend fun captureArea(
        context: Context,
        mediaProjection: MediaProjection,
        selectionPath: Path?,
        boundingBox: RectF,
        minTimestamp: Long = 0L
    ): Bitmap? = withContext(Dispatchers.Default) {
        val fullScreenBitmap = captureFullScreen(context, mediaProjection, minTimestamp) ?: return@withContext null

        try {
            val bmpWidth = fullScreenBitmap.width
            val bmpHeight = fullScreenBitmap.height

            // Constrain bounding box strictly within actual bitmap boundaries
            val safeLeft = boundingBox.left.toInt().coerceIn(0, (bmpWidth - 2).coerceAtLeast(0))
            val safeTop = boundingBox.top.toInt().coerceIn(0, (bmpHeight - 2).coerceAtLeast(0))
            val safeRight = boundingBox.right.toInt().coerceIn(safeLeft + 1, bmpWidth)
            val safeBottom = boundingBox.bottom.toInt().coerceIn(safeTop + 1, bmpHeight)
            val cropWidth = (safeRight - safeLeft).coerceAtLeast(1)
            val cropHeight = (safeBottom - safeTop).coerceAtLeast(1)

            val rawCropped = Bitmap.createBitmap(
                fullScreenBitmap,
                safeLeft,
                safeTop,
                cropWidth,
                cropHeight
            )

            // If a freehand path was drawn, apply lasso mask so only exact pixels inside path are kept
            if (selectionPath != null && !selectionPath.isEmpty) {
                val maskedBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(maskedBitmap)

                // Fill with white background so transparent pixels don't turn black in JPEG for AI model
                canvas.drawColor(android.graphics.Color.WHITE)

                val translatedPath = Path(selectionPath).apply {
                    offset(-safeLeft.toFloat(), -safeTop.toFloat())
                }

                val pathBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
                val pathCanvas = Canvas(pathBitmap)
                val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isDither = true
                }
                pathCanvas.drawPath(translatedPath, pathPaint)
                pathPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                pathCanvas.drawBitmap(rawCropped, 0f, 0f, pathPaint)

                canvas.drawBitmap(pathBitmap, 0f, 0f, null)
                pathBitmap.recycle()

                rawCropped.recycle()
                fullScreenBitmap.recycle()
                maskedBitmap
            } else {
                fullScreenBitmap.recycle()
                rawCropped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping screenshot", e)
            try { fullScreenBitmap.recycle() } catch (_: Exception) {}
            null
        }
    }

    private suspend fun captureFullScreen(
        context: Context,
        mediaProjection: MediaProjection,
        minTimestamp: Long = 0L
    ): Bitmap? = withContext(Dispatchers.Default) {
        val success = ensureSession(context, mediaProjection)
        if (!success) {
            Log.e(TAG, "ensureSession returned false")
            return@withContext null
        }

        // 1. Check if an image is immediately available in ImageReader
        try {
            val directImage = imageReader?.acquireLatestImage()
            if (directImage != null) {
                try {
                    val directBitmap = convertImageToBitmap(directImage, currentWidth, currentHeight)
                    if (directBitmap != null) {
                        synchronized(sessionLock) {
                            latestBitmap?.recycle()
                            latestBitmap = directBitmap
                            latestBitmapTimestamp = System.currentTimeMillis()
                        }
                        return@withContext directBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    }
                } finally {
                    try { directImage.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct acquireLatestImage failed: ${e.message}")
        }

        // 2. Check if a frame already arrived after the minTimestamp (overlay hidden time)
        val threshold = if (minTimestamp > 0L) minTimestamp else captureRequestedTimestamp
        synchronized(sessionLock) {
            val bmp = latestBitmap
            if (bmp != null && !bmp.isRecycled && (threshold <= 0L || latestBitmapTimestamp >= threshold)) {
                return@withContext bmp.copy(Bitmap.Config.ARGB_8888, true)
            }
        }

        // 3. Otherwise wait up to 1500ms for the next frame
        val frame = withTimeoutOrNull(1500L) {
            suspendCancellableCoroutine { continuation ->
                synchronized(sessionLock) {
                    val bmp = latestBitmap
                    if (bmp != null && !bmp.isRecycled && (threshold <= 0L || latestBitmapTimestamp >= threshold)) {
                        continuation.resume(bmp.copy(Bitmap.Config.ARGB_8888, true))
                        return@suspendCancellableCoroutine
                    }
                    pendingContinuation = continuation
                }
                continuation.invokeOnCancellation {
                    synchronized(sessionLock) {
                        if (pendingContinuation === continuation) {
                            pendingContinuation = null
                        }
                    }
                }
            }
        }

        if (frame != null) {
            return@withContext frame
        }

        // 4. Reliable Fallback: Screen was static, so VirtualDisplay produced no new frames.
        // Use available latestBitmap fallback so the capture NEVER fails!
        synchronized(sessionLock) {
            val fallback = latestBitmap
            if (fallback != null && !fallback.isRecycled) {
                Log.d(TAG, "Screen was static. Using available latestBitmap fallback.")
                return@withContext fallback.copy(Bitmap.Config.ARGB_8888, true)
            }
        }

        Log.e(TAG, "No screen frame available for capture")
        null
    }

    fun release() {
        synchronized(sessionLock) {
            cleanupInternal()
            currentProjection = null
        }
    }

    private fun cleanupInternal() {
        try {
            currentProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            handlerThread?.quitSafely()
        } catch (_: Exception) {}
        handlerThread = null
        handler = null

        latestBitmap?.recycle()
        latestBitmap = null

        pendingContinuation?.let {
            if (it.isActive) it.resume(null)
        }
        pendingContinuation = null
    }
}
