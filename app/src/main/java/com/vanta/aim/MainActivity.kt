package com.vanta.aim

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startForegroundService(intent)
                startOverlay()
                status.text = "capture running — overlay active"
            } else {
                status.text = "capture permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        status = TextView(this).apply { text = "idle" }

        root.addView(Button(this).apply {
            text = "grant overlay"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        })

        root.addView(Button(this).apply {
            text = "start capture + overlay"
            setOnClickListener {
                val mgr = getSystemService(MediaProjectionManager::class.java)
                projectionLauncher.launch(mgr.createScreenCaptureIntent())
            }
        })

        root.addView(Button(this).apply {
            text = "request Shizuku"
            setOnClickListener { ShizukuBridge.requestPermission(this) }
        })

        root.addView(status)
        setContentView(root)
    }

    private fun startOverlay() {
        startService(Intent(this, OverlayService::class.java))
    }
}
