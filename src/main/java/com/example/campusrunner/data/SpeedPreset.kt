package com.example.campusrunner.data

enum class SpeedPreset(
    val label: String,
    val speedMps: Double
) {
    FAST_PACE("3.10配速", 5.26),
    FOUR_MIN_PACE("4分配", 4.17),
    SEVEN_MIN_PACE("7分配", 2.38);

    companion object {
        fun closestTo(speedMps: Double): SpeedPreset {
            return entries.minBy { kotlin.math.abs(it.speedMps - speedMps) }
        }
    }
}
