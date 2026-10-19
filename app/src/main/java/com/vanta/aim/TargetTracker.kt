package com.vanta.aim

import kotlin.math.hypot

class TargetTracker(private val maxLostFrames: Int = 12) {

    private data class Track(
        val id: Int,
        var cx: Float, var cy: Float,
        var lost: Int = 0
    )

    private var nextId = 0
    private val tracks = mutableListOf<Track>()
    var lockedId: Int? = null

    fun update(detections: List<Detection>): List<Detection> {
        val assigned = mutableSetOf<Int>()

        for (t in tracks) {
            var best: Detection? = null
            var bestDist = Float.MAX_VALUE
            for ((i, d) in detections.withIndex()) {
                if (i in assigned) continue
                val dist = hypot(d.cx - t.cx, d.cy - t.cy)
                if (dist < bestDist && dist < 80f) {
                    bestDist = dist; best = d; assigned.add(i)
                }
            }
            if (best != null) {
                t.cx = best.cx; t.cy = best.cy; t.lost = 0
            } else t.lost++
        }

        for ((i, d) in detections.withIndex()) {
            if (i !in assigned) {
                tracks.add(Track(nextId++, d.cx, d.cy))
            }
        }

        tracks.removeAll { it.lost > maxLostFrames }

        // Lock persists until the tracked target disappears for maxLostFrames.
        val locked = tracks.find { it.id == lockedId }
        if (locked == null || locked.lost > maxLostFrames) lockedId = null

        return detections
    }

    fun pickTarget(detections: List<Detection>, screenCx: Float, screenCy: Float): Detection? {
        if (lockedId != null) {
            val t = tracks.find { it.id == lockedId } ?: return null
            return detections.minByOrNull { hypot(it.cx - t.cx, it.cy - t.cy) }
        }
        return detections.minByOrNull { hypot(it.cx - screenCx, it.cy - screenCy) }
    }

    fun lock(target: Detection) {
        val t = tracks.minByOrNull { hypot(it.cx - target.cx, it.cy - target.cy) }
        lockedId = t?.id
    }

    fun release() { lockedId = null }
}
