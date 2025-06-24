package com.ner.wimap

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Coordinates(val latitude: Double, val longitude: Double)

class LocationProvider(private val application: Application) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    private val _currentLocation = MutableStateFlow<Coordinates?>(null)
    val currentLocation: StateFlow<Coordinates?> = _currentLocation.asStateFlow()

    private var locationCallback: LocationCallback? = null

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            _currentLocation.value = null // Or post an error/status
            // Consider logging or signaling that permission is missing
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L) // 10 seconds interval
            .setMinUpdateIntervalMillis(5000L) // 5 seconds minimum interval
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    _currentLocation.value = Coordinates(it.latitude, it.longitude)
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    // Location is not available (e.g., GPS turned off)
                    // _currentLocation.value = null // Or handle as needed
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            _currentLocation.value = null
            // Log e
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    fun getLastKnownLocation() {
        if (!hasLocationPermission()) {
            _currentLocation.value = null
            return
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    _currentLocation.value = Coordinates(it.latitude, it.longitude)
                }
            }
        } catch (e: SecurityException) {
            _currentLocation.value = null
        }
    }
}
