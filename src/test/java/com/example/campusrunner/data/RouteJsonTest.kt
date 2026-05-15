package com.example.campusrunner.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteJsonTest {
    @Test
    fun encodeDecodeRoutesRoundTrip() {
        val routes = listOf(
            SavedRoute(
                id = "route-1",
                name = "操场一圈",
                points = listOf(
                    RoutePoint(39.0, 116.0),
                    RoutePoint(39.001, 116.001)
                )
            )
        )

        val decoded = RouteJson.decode(RouteJson.encode(routes))

        assertEquals(routes, decoded)
    }

    @Test
    fun invalidJsonReturnsEmptyList() {
        assertEquals(emptyList<SavedRoute>(), RouteJson.decode("{bad json"))
    }
}
