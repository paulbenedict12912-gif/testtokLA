package com.vanta.aim

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureService : Service() {

    companion object {
        var imageReader: ImageReader? = null
        var screenWidth = 0
        var screenHeight = 0
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, notification())

        val code = intent?.getIntExtra("resultCode", 0) ?: return START_NOT_STICKY
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mgr = getSystemService(MediaProjectionManager::class.java)
        projection = mgr.getMediaProjection(code, data)

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = projection?.createVirtualDisplay(
            "aim-capture",
            screenWidth, screenHeight, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        return START_STICKY
    }

    private fun createChannel() {
        val ch = NotificationChannel("capture", "Capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun notification(): Notification =
        Notification.Builder(this, "capture")
            .setContentTitle("AimAssist")
            .setContentText("screen capture active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

    override fun onDestroy() {
        virtualDisplay?.release()
        projection?.stop()
        imageReader?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
