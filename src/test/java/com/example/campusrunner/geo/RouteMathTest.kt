package com.example.campusrunner.geo

import com.example.campusrunner.data.PlaybackMode
import com.example.campusrunner.data.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMathTest {
    @Test
    fun totalDistanceSumsSegments() {
        val points = listOf(
            RoutePoint(39.0, 116.0),
            RoutePoint(39.0, 116.001),
            RoutePoint(39.001, 116.001)
        )

        val distance = RouteMath.totalDistanceMeters(points)

        assertTrue(distance in 195.0..205.0)
    }

    @Test
    fun loopPlaybackWrapsAroundToBeginning() {
        val points = listOf(
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 0.001),
            RoutePoint(0.001, 0.001),
            RoutePoint(0.0, 0.0)
        )
        val loopDistance = RouteMath.totalDistanceMeters(points, closeLoop = true)

        val loc = RouteMath.interpolateRoute(
            points = points,
            elapsedMillis = ((loopDistance + 10.0) * 1000).toLong(),
            speedMps = 1.0,
            playbackMode = PlaybackMode.LOOP
        )

        val distanceFromStart = RouteMath.distanceMeters(RoutePoint(0.0, 0.0), RoutePoint(loc.latWgs84, loc.lngWgs84))
        assertTrue(distanceFromStart < 15.0)
    }

    @Test
    fun outAndBackPlaybackReturnsTowardStart() {
        val points = listOf(
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 0.001)
        )
        val oneWayDistance = RouteMath.totalDistanceMeters(points)

        val loc = RouteMath.interpolateRoute(
            points = points,
            elapsedMillis = ((oneWayDistance + 20.0) * 1000).toLong(),
            speedMps = 1.0,
            playbackMode = PlaybackMode.OUT_AND_BACK
        )

        val distanceFromEnd = RouteMath.distanceMeters(points.last(), RoutePoint(loc.latWgs84, loc.lngWgs84))
        assertEquals(20.0, distanceFromEnd, 1.5)
    }
}
