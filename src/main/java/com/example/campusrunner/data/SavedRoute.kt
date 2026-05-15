package com.example.campusrunner.data

data class SavedRoute(
    val id: String,
    val name: String,
    val points: List<RoutePoint>,
    val speedMps: Double = 2.6,
    val closeLoop: Boolean = false,
    val loopCount: Int = 1
)
