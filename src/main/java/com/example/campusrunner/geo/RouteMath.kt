package com.example.campusrunner.geo

import com.example.campusrunner.data.PlaybackMode
import com.example.campusrunner.data.RoutePoint
import com.example.campusrunner.data.SimulatedLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RouteMath {
    private const val EARTH_RADIUS_METERS = 6378137.0
    private const val LOOP_CLOSE_THRESHOLD_METERS = 35.0

    fun totalDistanceMeters(points: List<RoutePoint>, closeLoop: Boolean = false): Double {
        if (points.size < 2) {
            return 0.0
        }
        val segmentDistance = points.zipWithNext().sumOf { (from, to) -> distanceMeters(from, to) }
        return if (closeLoop) {
            segmentDistance + distanceMeters(points.last(), points.first())
        } else {
            segmentDistance
        }
    }

    fun inferredPlaybackMode(points: List<RoutePoint>): PlaybackMode {
        if (points.size < 3) {
            return PlaybackMode.OUT_AND_BACK
        }
        return if (distanceMeters(points.first(), points.last()) <= LOOP_CLOSE_THRESHOLD_METERS) {
            PlaybackMode.LOOP
        } else {
            PlaybackMode.OUT_AND_BACK
        }
    }

    fun interpolateRoute(
        points: List<RoutePoint>,
        elapsedMillis: Long,
        speedMps: Double,
        playbackMode: PlaybackMode = inferredPlaybackMode(points)
    ): SimulatedLocation {
        val first = points.firstOrNull() ?: RoutePoint(0.0, 0.0)
        if (points.size < 2 || speedMps <= 0.0 || elapsedMillis <= 0L) {
            return SimulatedLocation(first.latWgs84, first.lngWgs84, 0f, 0f, 5f, 10.0)
        }

        val routeDistance = totalDistanceMeters(points)
        if (routeDistance <= 0.0) {
            return SimulatedLocation(first.latWgs84, first.lngWgs84, 0f, 0f, 5f, 10.0)
        }

        val traveled = speedMps * elapsedMillis / 1000.0
        val distanceOnPath = when (playbackMode) {
            PlaybackMode.LOOP -> {
                val closingDistance = distanceMeters(points.last(), points.first())
                val loopDistance = routeDistance + closingDistance
                if (loopDistance <= 0.0) 0.0 else positiveModulo(traveled, loopDistance)
            }
            PlaybackMode.OUT_AND_BACK -> {
                val cycleDistance = routeDistance * 2
                val cyclePosition = positiveModulo(traveled, cycleDistance)
                if (cyclePosition <= routeDistance) cyclePosition else cycleDistance - cyclePosition
            }
        }

        val effectivePoints = if (playbackMode == PlaybackMode.LOOP) points + points.first() else points
        val (from, to, segmentOffset) = locateSegment(effectivePoints, distanceOnPath)
        val bearing = bearingDegrees(from, to)
        val interpolated = moveAlong(from, bearing, segmentOffset)

        return SimulatedLocation(
            latWgs84 = interpolated.latWgs84,
            lngWgs84 = interpolated.lngWgs84,
            speedMps = speedMps.toFloat(),
            bearing = bearing.toFloat(),
            accuracyMeters = 5f,
            altitudeMeters = 10.0
        )
    }

    fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val lat1 = Math.toRadians(a.latWgs84)
        val lat2 = Math.toRadians(b.latWgs84)
        val dLat = Math.toRadians(b.latWgs84 - a.latWgs84)
        val dLng = Math.toRadians(b.lngWgs84 - a.lngWgs84)
        val h = sin(dLat / 2).let { it * it } +
                cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDegrees(from: RoutePoint, to: RoutePoint): Double {
        val lat1 = Math.toRadians(from.latWgs84)
        val lat2 = Math.toRadians(to.latWgs84)
        val dLng = Math.toRadians(to.lngWgs84 - from.lngWgs84)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun locateSegment(points: List<RoutePoint>, distanceOnPath: Double): Triple<RoutePoint, RoutePoint, Double> {
        var remaining = distanceOnPath
        points.zipWithNext().forEach { (from, to) ->
            val segmentLength = distanceMeters(from, to)
            if (remaining <= segmentLength) {
                return Triple(from, to, remaining)
            }
            remaining -= segmentLength
        }
        val from = points[points.lastIndex - 1]
        val to = points.last()
        return Triple(from, to, distanceMeters(from, to))
    }

    private fun moveAlong(from: RoutePoint, bearingDegrees: Double, distanceMeters: Double): RoutePoint {
        val bearingRad = Math.toRadians(bearingDegrees)
        val fromLatRad = Math.toRadians(from.latWgs84)
        val fromLngRad = Math.toRadians(from.lngWgs84)
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS

        val latRad = kotlin.math.asin(
            sin(fromLatRad) * cos(angularDistance) +
                    cos(fromLatRad) * sin(angularDistance) * cos(bearingRad)
        )
        val lngRad = fromLngRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(fromLatRad),
            cos(angularDistance) - sin(fromLatRad) * sin(latRad)
        )
        return RoutePoint(Math.toDegrees(latRad), Math.toDegrees(lngRad))
    }

    private fun positiveModulo(value: Double, mod: Double): Double {
        return ((value % mod) + mod) % mod
    }
}
