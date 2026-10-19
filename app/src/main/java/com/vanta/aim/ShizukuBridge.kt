package com.vanta.aim

import android.content.pm.PackageManager
import android.view.MotionEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object ShizukuBridge {

    private const val REQUEST_CODE = 1001

    fun requestPermission(activity: android.app.Activity) {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun isReady(): Boolean =
        Shizuku.pingBinder() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    @Suppress("PrivateApi")
    fun injectTap(x: Float, y: Float) {
        if (!isReady()) return
        val inputManager = SystemServiceHelper.getSystemService("input")
        val wrapper = ShizukuBinderWrapper(inputManager)
        val cls = Class.forName("android.hardware.input.InputManager")
        val method: Method = cls.getMethod(
            "injectInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val now = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 8, MotionEvent.ACTION_UP, x, y, 0)
        down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN

        method.invoke(wrapper, down, 0)
        method.invoke(wrapper, up, 0)
        down.recycle(); up.recycle()
    }
}
