package com.vanta.aim

import ai.onnxruntime.*
import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val cx: Float, val cy: Float,
    val w: Float, val h: Float,
    val score: Float,
    val cls: Int
)

class DetectionEngine(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val inputSize: Int = 256,
    private val confThreshold: Float = 0.35f
) {

    fun detect(bitmap: Bitmap): List<Detection> {
        val (input, scale, padX, padY) = preprocess(bitmap)
        val tensor = OnnxTensor.createTensor(env, input)
        val output = session.run(mapOf(session.inputNames.first() to tensor))
        val raw = output[0].value as Array<Array<FloatArray>>
        tensor.close()
        return postprocess(raw, scale, padX, padY, bitmap.width, bitmap.height)
    }

    private fun preprocess(src: Bitmap): Preprocessed {
        val scale = min(inputSize.toFloat() / src.width, inputSize.toFloat() / src.height)
        val nw = (src.width * scale).toInt()
        val nh = (src.height * scale).toInt()
        val padX = (inputSize - nw) / 2
        val padY = (inputSize - nh) / 2

        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val input = Array(1) { Array(3) { Array(inputSize) { FloatArray(inputSize) } } }

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val sx = x - padX
                val sy = y - padY
                if (sx in 0 until nw && sy in 0 until nh) {
                    val p = resized.getPixel(sx, sy)
                    input[0][0][y][x] = ((p shr 16) and 0xFF) / 255f
                    input[0][1][y][x] = ((p shr 8) and 0xFF) / 255f
                    input[0][2][y][x] = (p and 0xFF) / 255f
                }
            }
        }
        return Preprocessed(input, scale, padX.toFloat(), padY.toFloat())
    }

    private fun postprocess(
        raw: Array<Array<FloatArray>>,
        scale: Float, padX: Float, padY: Float,
        origW: Int, origH: Int
    ): List<Detection> {
        // YOLOv8 output: [1, 4+nc, 8400] transposed into raw[0]
        val out: MutableList<Detection> = mutableListOf()
        val rows = raw[0].size
        val cols = raw[0][0].size
        for (i in 0 until cols) {
            var best = 0f
            var bestCls = -1
            for (c in 4 until rows) {
                val s = raw[0][c][i]
                if (s > best) { best = s; bestCls = c - 4 }
            }
            if (best < confThreshold) continue
            val cx = (raw[0][0][i] - padX) / scale
            val cy = (raw[0][1][i] - padY) / scale
            val w = raw[0][2][i] / scale
            val h = raw[0][3][i] / scale
            if (cx < 0 || cy < 0 || cx > origW || cy > origH) continue
            out.add(Detection(cx, cy, w, h, best, bestCls))
        }
        return nms(out)
    }

    private fun nms(dets: List<Detection>, iouThresh: Float = 0.45f): List<Detection> {
        val sorted = dets.sortedByDescending { it.score }.toMutableList()
        val kept = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val a = sorted.removeAt(0)
            kept.add(a)
            sorted.removeAll { b ->
                val ix = max(0f, min(a.cx + a.w / 2, b.cx + b.w / 2) - max(a.cx - a.w / 2, b.cx - b.w / 2))
                val iy = max(0f, min(a.cy + a.h / 2, b.cy + b.h / 2) - max(a.cy - a.h / 2, b.cy - b.h / 2))
                val inter = ix * iy
                val union = a.w * a.h + b.w * b.h - inter
                inter / union > iouThresh
            }
        }
        return kept
    }

    data class Preprocessed(
        val input: Array<Array<Array<FloatArray>>>,
        val scale: Float, val padX: Float, val padY: Float
    )
}
