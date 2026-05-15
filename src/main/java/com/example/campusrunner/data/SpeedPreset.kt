package com.example.campusrunner.data

enum class SpeedPreset(
    val label: String,
    val speedMps: Double
) {
    WALK("走路", 1.4),
    RUN("跑步", 3.0),
    RIDE("骑行", 5.5);

    companion object {
        fun closestTo(speedMps: Double): SpeedPreset {
            return entries.minBy { kotlin.math.abs(it.speedMps - speedMps) }
        }
    }
}
