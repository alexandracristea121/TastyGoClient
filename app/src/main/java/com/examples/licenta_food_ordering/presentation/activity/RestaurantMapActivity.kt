package com.examples.licenta_food_ordering.presentation.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.licenta_food_ordering.R
import com.examples.licenta_food_ordering.model.food.MenuItem
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.database.*

class RestaurantMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: FirebaseDatabase
    private lateinit var restaurantsRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_map)

        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true

        database = FirebaseDatabase.getInstance()
        restaurantsRef = database.getReference("Restaurants")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (isGooglePlayServicesAvailable()) {
            val mapFragment = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map, mapFragment)
                .commit()

            mapFragment.getMapAsync(this)
        } else {
            Toast.makeText(this, "Google Play Services not available.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } ?: run {
                        Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }

        fetchRestaurants()
    }

    private fun fetchRestaurants() {
        restaurantsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (restaurantSnapshot in snapshot.children) {
                    val restaurant = restaurantSnapshot.getValue(Restaurant::class.java)
                    if (restaurant != null) {
                        val restaurantLocation = LatLng(restaurant.latitude, restaurant.longitude)
                        val marker = mMap.addMarker(
                            MarkerOptions()
                                .position(restaurantLocation)
                                .title(restaurant.name)
                        )

                        marker?.tag = restaurant.id
                    }
                }

                mMap.setOnMarkerClickListener { marker ->
                    val restaurantId = marker.tag as? String
                    if (restaurantId != null) {
                        val intent = Intent(
                            this@RestaurantMapActivity,
                            RestaurantDetailsActivity::class.java
                        )
                        intent.putExtra("RESTAURANT_ID", restaurantId)
                        startActivity(intent)
                    }
                    true
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@RestaurantMapActivity,
                    "Error fetching restaurants",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onMapReady(mMap)
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class Restaurant(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    var menu: Map<String, MenuItem>? = null,
    val categories: List<String> = listOf(),
    val adminUserId: String = "",
    val phoneNumber: String = "",
    val logo: String = "",
) {
    constructor() : this(
        id = "",
        name = "",
        latitude = 0.0,
        longitude = 0.0,
        address = "",
        menu = null,
        categories = listOf(),
        adminUserId = "",
        phoneNumber = "",
        logo = ""
    )
}
