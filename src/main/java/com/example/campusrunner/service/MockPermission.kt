package com.example.campusrunner.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build

object MockPermission {
    @SuppressLint("MissingPermission")
    fun canUseMockLocation(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = "campus_runner_probe"
        return try {
            addProvider(locationManager, provider)
            locationManager.setTestProviderEnabled(provider, true)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            runCatching {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("WrongConstant")
    fun addProvider(locationManager: LocationManager, provider: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
        } else {
            locationManager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
        }
    }
}
