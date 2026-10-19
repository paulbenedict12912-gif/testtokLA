package com.vanta.aim

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.*
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.*
import java.io.File

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var engine: DetectionEngine
    private val tracker = TargetTracker()
    private var aimEnabled = true
    private var lastInject = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()
        loadModel()
        startLoop()
    }

    private fun buildOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = OverlayView(this).apply {
            onToggle = { aimEnabled = !aimEnabled; invalidate() }
            onRelease = { tracker.release(); invalidate() }
        }
        windowManager.addView(overlayView, params)
    }

    private fun loadModel() {
        val env = OrtEnvironment.getEnvironment()
        val modelFile = File(filesDir, "aim.onnx")
        if (!modelFile.exists()) {
            assets.open("models/aim.onnx").use { input ->
                modelFile.outputStream().use { input.copyTo(it) }
            }
        }
        val session = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        engine = DetectionEngine(env, session, inputSize = 256, confThreshold = 0.35f)
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                val reader = ScreenCaptureService.imageReader
                if (reader == null) { delay(16); continue }

                val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
                if (image == null) { delay(8); continue }

                try {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    val detections = engine.detect(bitmap)
                    tracker.update(detections)

                    val cx = ScreenCaptureService.screenWidth / 2f
                    val cy = ScreenCaptureService.screenHeight / 2f
                    val target = tracker.pickTarget(detections, cx, cy)

                    if (target != null) {
                        tracker.lock(target)
                        val now = System.currentTimeMillis()
                        if (aimEnabled && now - lastInject > 40) { // 25 Hz max injection
                            lastInject = now
                            ShizukuBridge.injectTap(target.cx, target.cy)
                        }
                    } else {
                        tracker.release()
                    }

                    withContext(Dispatchers.Main) {
                        overlayView.targets = detections
                        overlayView.locked = target
                        overlayView.invalidate()
                    }
                } catch (_: Exception) {
                    // frame dropped; loop continues on next capture
                }
                delay(8)
            }
        }
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        bmp.recycle()
        return cropped
    }

    override fun onDestroy() {
        scope.cancel()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
