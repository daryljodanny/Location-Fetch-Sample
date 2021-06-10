package com.daryl.locationfetch

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.daryl.locationfetch.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val geocoder by lazy {
        Geocoder(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.btnFetchLocation.setOnClickListener {
            onButtonPressed()
        }
    }

    private fun setLocation(location: Location) {
        binding.progressIndicator.hide()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        binding.textLatitude.text = location.latitude.toString()
        binding.textLongitude.text = location.longitude.toString()
        val addressList = geocoder.getFromLocation(location.latitude, location.longitude, 10)
        addressList[0]?.let { address ->
            binding.textCountry.text = address.countryName
            binding.textCountryCode.text = address.countryCode
        }
    }

    private fun onButtonPressed() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> fetchLastLocation()

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> openPermissionRationaleDialog()

            else -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun fetchLastLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                setLocation(location)
            } else {
                createLocationRequest()
            }
        }
    }

    private fun createLocationRequest() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1 * 1000
            isWaitForAccurateLocation = true
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            binding.progressIndicator.show()
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    settingsLauncher.launch(
                        IntentSenderRequest.Builder(exception.resolution).build()
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun openPermissionRationaleDialog() {
        Snackbar.make(
            binding.root,
            getString(R.string.error_location_permission),
            Snackbar.LENGTH_LONG
        ).setAction("Allow") {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }.show()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                fetchLastLocation()
            } else {
                Snackbar.make(
                    binding.root,
                    "Location permission is required to fetch the PIN Code",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult?.lastLocation?.let {
                setLocation(it)
            }
        }
    }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            when (it.resultCode) {
                Activity.RESULT_OK -> fetchLastLocation()
                Activity.RESULT_CANCELED -> Snackbar.make(
                    binding.root,
                    "Location permission is required to fetch the PIN Code",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
