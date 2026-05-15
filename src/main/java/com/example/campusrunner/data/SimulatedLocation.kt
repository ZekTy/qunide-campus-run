package com.example.campusrunner.data

data class SimulatedLocation(
    val latWgs84: Double,
    val lngWgs84: Double,
    val speedMps: Float,
    val bearing: Float,
    val accuracyMeters: Float,
    val altitudeMeters: Double
)
