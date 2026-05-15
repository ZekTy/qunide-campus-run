package com.example.campusrunner.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateUtilsTest {
    @Test
    fun detectsChinaBounds() {
        assertTrue(CoordinateUtils.isInChina(39.9042, 116.4074))
        assertFalse(CoordinateUtils.isInChina(37.7749, -122.4194))
    }

    @Test
    fun wgsGcjRoundTripStaysClose() {
        val beijingLat = 39.9042
        val beijingLng = 116.4074

        val gcj = CoordinateUtils.wgs84ToGcj02(beijingLat, beijingLng)
        val wgs = CoordinateUtils.gcj02ToWgs84(gcj.lat, gcj.lng)

        assertEquals(beijingLat, wgs.lat, 0.0002)
        assertEquals(beijingLng, wgs.lng, 0.0002)
    }
}
