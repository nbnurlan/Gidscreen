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
    private var reusableBitmap: Bitmap? = null
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

                    // CRITICAL EFFICIENCY FIX:
                    // Only process and allocate Bitmaps when a capture is actively requested.
                    // When idle, closing the image buffer immediately uses 0 CPU and 0 MB memory allocations.
                    val cont = synchronized(sessionLock) {
                        val c = pendingContinuation
                        pendingContinuation = null
                        c
                    }

                    if (cont != null && cont.isActive) {
                        val bitmap = convertImageToBitmap(image, width, height)
                        synchronized(sessionLock) {
                            reusableBitmap?.recycle()
                            reusableBitmap = bitmap
                        }
                        cont.resume(bitmap?.copy(Bitmap.Config.ARGB_8888, true))
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
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * targetWidth

        return try {
            if (rowPadding == 0 && buffer.remaining() >= targetWidth * targetHeight * pixelStride) {
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap
            } else {
                // Handle hardware stride padding
                val cleanBuffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 4)
                val rowBytes = targetWidth * pixelStride
                for (row in 0 until targetHeight) {
                    val srcPos = row * rowStride
                    buffer.position(srcPos)
                    val oldLimit = buffer.limit()
                    val availableInRow = (buffer.capacity() - srcPos).coerceAtLeast(0)
                    val bytesToCopy = rowBytes.coerceAtMost(availableInRow)
                    buffer.limit(srcPos + bytesToCopy)
                    cleanBuffer.put(buffer)
                    buffer.limit(oldLimit)
                }
                cleanBuffer.rewind()
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(cleanBuffer)
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert hardware Image to Bitmap", e)
            null
        }
    }

    /**
     * Invalidates any previously stored frame to guarantee that the next capture
     * grabs a brand-new frame rendered after overlay layers are hidden.
     */
    fun invalidateFrame() {
        synchronized(sessionLock) {
            reusableBitmap?.recycle()
            reusableBitmap = null
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
        boundingBox: RectF
    ): Bitmap? = withContext(Dispatchers.Default) {
        val fullScreenBitmap = captureFullScreen(context, mediaProjection) ?: return@withContext null

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
            fullScreenBitmap.recycle()
            null
        }
    }

    private suspend fun captureFullScreen(
        context: Context,
        mediaProjection: MediaProjection
    ): Bitmap? = withContext(Dispatchers.Default) {
        val success = ensureSession(context, mediaProjection)
        if (!success) return@withContext null

        // Wait up to 3 seconds for the new frame rendered after overlays were hidden
        return@withContext withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine { continuation ->
                synchronized(sessionLock) {
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

        reusableBitmap?.recycle()
        reusableBitmap = null

        pendingContinuation?.let {
            if (it.isActive) it.resume(null)
        }
        pendingContinuation = null
    }
}
