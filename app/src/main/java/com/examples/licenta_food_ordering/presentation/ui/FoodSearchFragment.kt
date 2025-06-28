@file:Suppress("DEPRECATION")

package com.examples.licenta_food_ordering.presentation.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.FragmentSearchBinding
import com.examples.licenta_food_ordering.model.courier.Courier
import com.examples.licenta_food_ordering.model.courier.CourierStatus
import com.examples.licenta_food_ordering.presentation.activity.Restaurant
import com.examples.licenta_food_ordering.presentation.activity.RestaurantDetailsActivity
import com.examples.licenta_food_ordering.service.courier.map.RouteManager
import com.examples.licenta_food_ordering.service.courier.tracking.FirebaseCourierService
import com.examples.licenta_food_ordering.utils.ConfigUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class FoodSearchFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: FirebaseDatabase
    private lateinit var restaurantsRef: DatabaseReference
    private lateinit var firebaseCourierService: FirebaseCourierService
    private val restaurantsList = mutableListOf<Restaurant>()
    private lateinit var binding: FragmentSearchBinding
    private lateinit var placesClient: PlacesClient
    private val courierMarkers = mutableMapOf<String, Marker>()
    private val courierRoutes = mutableMapOf<String, List<LatLng>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)

        if (!Places.isInitialized()) {
            Places.initialize(
                requireContext(),
                "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"
            )
        }

        placesClient = Places.createClient(requireContext())

        database = FirebaseDatabase.getInstance()
        restaurantsRef = database.getReference("Restaurants")
        firebaseCourierService = FirebaseCourierService()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupSearchView()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ConfigUtils.initialize(requireContext())

        Places.initialize(requireContext(), ConfigUtils.getGooglePlacesApiKey())
        placesClient = Places.createClient(requireContext())
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isScrollGesturesEnabled = true
        mMap.uiSettings.isRotateGesturesEnabled = true
        mMap.uiSettings.isTiltGesturesEnabled = true

        mMap.apply {
            setMinZoomPreference(4f)
            setMaxZoomPreference(18f)
            
            setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isBuildingsEnabled = false
                    isIndoorEnabled = false
                }
            }
            
            setOnCameraIdleListener {
                isBuildingsEnabled = true
                isIndoorEnabled = true
            }
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
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
                        Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        } else {
            requestLocationPermissions()
        }

        fetchFoodPlacesNearCurrentLocation()
        fetchRestaurants()
        fetchAndShowCouriersOnMap()
    }

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private fun fetchFoodPlacesNearCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val geocoder = Geocoder(requireContext())
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val cityName = addresses[0].locality
                        val apiKey =
                            "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"

                        val query =
                            "restaurants OR fast food OR cafes OR bakeries OR food places OR dining OR takeout in $cityName"

                        val url = "https://maps.googleapis.com/maps/api/place/textsearch/json" +
                                "?query=${Uri.encode(query)}" +
                                "&key=$apiKey"

                        fetchPaginatedPlaces(url)
                    }
                } ?: run {
                    Log.e("fetchFoodPlaces", "Location is null")
                    Toast.makeText(
                        requireContext(),
                        "Unable to fetch current location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.addOnFailureListener {
                Log.e("fetchFoodPlaces", "Failed to get last location: ${it.message}")
                Toast.makeText(
                    requireContext(),
                    "Unable to fetch current location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchPaginatedPlaces(url: String) {
        val request = StringRequest(com.android.volley.Request.Method.GET, url, { response ->
            try {
                val jsonResponse = JSONObject(response)
                val results = jsonResponse.getJSONArray("results")

                val placeIds = mutableListOf<String>()
                for (i in 0 until results.length()) {
                    val place = results.getJSONObject(i)
                    val placeId = place.getString("place_id")
                    placeIds.add(placeId)
                    place.getJSONObject("geometry").getJSONObject("location").getDouble("lat")
                    place.getJSONObject("geometry").getJSONObject("location").getDouble("lng")
                    place.getString("name")
                }

                GlobalScope.launch(Dispatchers.IO) {
                    val deferreds = placeIds.map { placeId ->
                        async {
                            fetchPlaceDetailsAsync(placeId)
                        }
                    }
                    val placeDetails = deferreds.awaitAll()
                    placeDetails.forEach { details ->
                        details?.let {
                        }
                    }
                }

                val nextPageToken = jsonResponse.optString("next_page_token", null.toString())
                if (!nextPageToken.isNullOrEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        val nextPageUrl = "$url&pagetoken=$nextPageToken"
                        fetchPaginatedPlaces(nextPageUrl)
                    }, 5000)
                }
            } catch (e: JSONException) {
                Log.e("fetchFoodPlaces", "Error parsing JSON: ${e.message}")
            }
        }, { error ->
            Log.e("fetchFoodPlaces", "Error fetching food places: ${error.message}")
        })

        val requestQueue = Volley.newRequestQueue(requireContext())
        requestQueue.add(request)
    }

    private suspend fun fetchPlaceDetailsAsync(placeId: String): Map<String, String>? {
        val apiKey = "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"
        val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?placeid=$placeId" +
                "&key=$apiKey"

        return withContext(Dispatchers.IO) {
            try {
                val result = StringRequest(com.android.volley.Request.Method.GET, url, { response ->
                    val jsonResponse = JSONObject(response)
                    val resultObj = jsonResponse.getJSONObject("result")
                    val phone = resultObj.optString("formatted_phone_number", "Not available")
                    val website = resultObj.optString("website", "Not available")
                    val openingHours = resultObj.optJSONObject("opening_hours")
                    val openHours = openingHours?.optJSONArray("weekday_text")?.let {
                        val hoursList = mutableListOf<String>()
                        for (i in 0 until it.length()) {
                            hoursList.add(it.getString(i))
                        }
                        hoursList.joinToString(", ")
                    } ?: "Not available"
                    mapOf("phone" to phone, "website" to website, "open_hours" to openHours)
                }, { error ->
                    Log.e("fetchPlaceDetails", "Error fetching place details: ${error.message}")
                })

                val requestQueue = Volley.newRequestQueue(requireContext())
                requestQueue.add(result)
                null
            } catch (e: Exception) {
                Log.e(
                    "fetchPlaceDetails",
                    "Error fetching place details asynchronously: ${e.message}"
                )
                null
            }
        }
    }

    private fun fetchRestaurants() {
        restaurantsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                restaurantsList.clear()
                for (restaurantSnapshot in snapshot.children) {
                    val restaurant = restaurantSnapshot.getValue(Restaurant::class.java)
                    restaurant?.let {
                        restaurantsList.add(it)
                    }
                }
                showRestaurantsOnMap(restaurantsList)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Error fetching restaurants", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun showRestaurantsOnMap(restaurants: List<Restaurant>) {
        mMap.clear()

        for (restaurant in restaurants) {
            val restaurantLocation = LatLng(restaurant.latitude, restaurant.longitude)
            
            val markerIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(restaurantLocation)
                    .title(restaurant.name)
                    .icon(markerIcon)
                    .anchor(0.5f, 0.5f)
            )
            
            marker?.tag = "restaurant_${restaurant.id}"
        }

        mMap.setOnMarkerClickListener { marker ->
            if (marker.tag != null && marker.tag.toString().startsWith("restaurant_")) {
                val restaurantId = marker.tag.toString().removePrefix("restaurant_")
                val intent = Intent(requireContext(), RestaurantDetailsActivity::class.java)
                intent.putExtra("RESTAURANT_ID", restaurantId)
                startActivity(intent)
                true
            } else {
                val courierId = marker.tag as? String
                if (courierId != null) {
                    val route = courierRoutes[courierId]
                    if (route != null) {
                        RouteManager.toggleRoute(requireContext(), mMap, route)
                    }
                }
                false
            }
        }
    }

    private fun snapshotToCourier(snapshot: DataSnapshot): Courier {
        return Courier(
            id = snapshot.child("id").getValue(String::class.java) ?: "",
            name = snapshot.child("name").getValue(String::class.java) ?: "",
            latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0,
            longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0,
            restaurantLatitude = snapshot.child("restaurantLatitude").getValue(Double::class.java) ?: 0.0,
            restaurantLongitude = snapshot.child("restaurantLongitude").getValue(Double::class.java) ?: 0.0,
            userLatitude = snapshot.child("userLatitude").getValue(Double::class.java) ?: 0.0,
            userLongitude = snapshot.child("userLongitude").getValue(Double::class.java) ?: 0.0,
            userUid = snapshot.child("userUid").getValue(String::class.java) ?: "",
            orderId = snapshot.child("orderId").getValue(String::class.java) ?: "",
            status = try {
                CourierStatus.valueOf(snapshot.child("status").getValue(String::class.java) ?: "AVAILABLE")
            } catch (e: Exception) {
                CourierStatus.AVAILABLE
            },
            lastUpdate = snapshot.child("lastUpdate").getValue(Long::class.java) ?: System.currentTimeMillis(),
            minDistanceRaw = when (val raw = snapshot.child("minDistance").value) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull() ?: 0.0
                else -> 0.0
            },
            trafficEstimationInMinutes = snapshot.child("trafficEstimationInMinutes").getValue(Int::class.java) ?: 0
        )
    }

    private fun createCourierMarkerIcon(status: CourierStatus, estimationMinutes: Int): BitmapDescriptor {
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = when (status) {
            CourierStatus.DELIVERING -> Color.parseColor("#FF5722")
            CourierStatus.AVAILABLE -> Color.parseColor("#4CAF50")
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        iconPaint.color = Color.WHITE
        iconPaint.textSize = size * 0.4f
        iconPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🛵", size / 2f, size * 0.6f, iconPaint)

        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        statusPaint.color = Color.WHITE
        statusPaint.textSize = size * 0.15f
        statusPaint.textAlign = Paint.Align.CENTER
        val statusText = when (status) {
            CourierStatus.DELIVERING -> "${estimationMinutes}m"
            CourierStatus.AVAILABLE -> "✓"
        }
        canvas.drawText(statusText, size / 2f, size * 0.85f, statusPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun fetchAndShowCouriersOnMap() {
        val courierRef = FirebaseDatabase.getInstance().getReference("couriers")

        courierRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (courierSnapshot in snapshot.children) {
                    val courierId = courierSnapshot.key ?: continue

                    val courier = try {
                        snapshotToCourier(courierSnapshot)
                    } catch (e: Exception) {
                        Log.e("Firebase", "Failed to parse courier: ${e.message}")
                        continue
                    }

                    val currentLoc = LatLng(courier.latitude, courier.longitude)
                    val restaurantLoc = LatLng(courier.restaurantLatitude, courier.restaurantLongitude)
                    val userLoc = LatLng(courier.userLatitude, courier.userLongitude)

                    val orderId = courier.orderId
                    val orderRef = FirebaseDatabase.getInstance().getReference("orders").child(orderId)

                    orderRef.child("estimatedDeliveryTime")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                val estimationStr = dataSnapshot.getValue(String::class.java)
                                val estimationMinutes = estimationStr
                                    ?.filter { it.isDigit() }
                                    ?.toIntOrNull()
                                    ?.coerceAtLeast(1) ?: 1

                                val courierIcon = createCourierMarkerIcon(courier.status, estimationMinutes)

                                val existingMarker = courierMarkers[courierId]
                                if (existingMarker != null) {
                                    val previousPosition = existingMarker.position
                                    if (previousPosition != currentLoc) {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            simulateMarkerMovementWithTraffic(
                                                courierId,
                                                previousPosition,
                                                restaurantLoc,
                                                userLoc,
                                                estimationMinutes
                                            )
                                        }
                                    }
                                    existingMarker.setIcon(courierIcon)
                                } else {
                                    val newMarker = mMap.addMarker(
                                        MarkerOptions()
                                            .position(currentLoc)
                                            .title("Courier ${courier.name}")
                                            .snippet("Status: ${courier.status}")
                                            .icon(courierIcon)
                                    ) ?: return

                                    newMarker.tag = courierId
                                    courierMarkers[courierId] = newMarker
                                    courierRoutes[courierId] = listOf(currentLoc, restaurantLoc, userLoc)

                                    CoroutineScope(Dispatchers.Main).launch {
                                        simulateMarkerMovementWithTraffic(
                                            courierId,
                                            currentLoc,
                                            restaurantLoc,
                                            userLoc,
                                            estimationMinutes
                                        )
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("Map", "Failed to get delivery time: ${error.message}")
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Map", "Firebase error: ${error.message}")
            }
        })
    }
    private suspend fun simulateMarkerMovementWithTraffic(
        courierId: String,
        start: LatLng,
        restaurant: LatLng,
        destination: LatLng,
        trafficEstimationInMinutes: Int
    ) {
        val marker = courierMarkers[courierId] ?: return
        val totalDurationMs = (trafficEstimationInMinutes * 60 * 1000).coerceAtLeast(3000)

        val leg1Distance = distanceInMeters(start, restaurant)
        val leg2Distance = distanceInMeters(restaurant, destination)
        val totalDistance = leg1Distance + leg2Distance

        if (totalDistance <= 0.0) return

        val leg1Duration = (leg1Distance / totalDistance) * totalDurationMs
        val leg2Duration = (leg2Distance / totalDistance) * totalDurationMs

        animateMarker(marker, start, restaurant, leg1Duration.toLong())
        animateMarker(marker, restaurant, destination, leg2Duration.toLong())

        val courierRef = FirebaseDatabase.getInstance().getReference("couriers").child(courierId)
        
        val orderId = courierRef.child("orderId").get().await().getValue(String::class.java)
        
        if (!orderId.isNullOrEmpty()) {
            val orderRef = FirebaseDatabase.getInstance().getReference("orders").child(orderId)
            orderRef.child("orderDelivered").setValue(true).await()
        }

        val courierUpdates = mapOf(
            "status" to CourierStatus.AVAILABLE,
            "latitude" to destination.latitude,
            "longitude" to destination.longitude,
            "restaurantLatitude" to 0.0,
            "restaurantLongitude" to 0.0,
            "orderId" to ""
        )
        courierRef.updateChildren(courierUpdates).await()

        Toast.makeText(
            requireContext(),
            "Courier $courierId has delivered the order and is now available for new deliveries.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun distanceInMeters(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude, start.longitude,
            end.latitude, end.longitude,
            results
        )
        return results[0].toDouble()
    }

    private suspend fun animateMarker(
        marker: Marker,
        start: LatLng,
        end: LatLng,
        durationMs: Long
    ) {
        val interpolator = LatLngInterpolator.LinearFixed()
        val steps = 60
        val delayMs = durationMs / steps

        for (step in 1..steps) {
            val t = step.toFloat() / steps
            val newPos = interpolator.interpolate(t, start, end)
            marker.position = newPos
            delay(delayMs)
        }
    }

    interface LatLngInterpolator {
        fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng

        class LinearFixed : LatLngInterpolator {
            override fun interpolate(fraction: Float, a: LatLng, b: LatLng): LatLng {
                val lat = (b.latitude - a.latitude) * fraction + a.latitude
                val lng = (b.longitude - a.longitude) * fraction + a.longitude
                return LatLng(lat, lng)
            }
        }
    }


    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { query ->
                    searchRestaurantByName(query)
                }
            }
        })
    }

    private fun searchRestaurantByName(query: String) {
        val filteredRestaurants = restaurantsList.filter {
            it.name.contains(query, ignoreCase = true)
        }
        showRestaurantsOnMap(filteredRestaurants)

        if (filteredRestaurants.size == 1) {
            val restaurant = filteredRestaurants.first()
            val restaurantLocation = LatLng(restaurant.latitude, restaurant.longitude)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(restaurantLocation, 15f))
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permission granted", Toast.LENGTH_SHORT).show()
                mMap.isMyLocationEnabled = true
                fetchFoodPlacesNearCurrentLocation()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission to access location denied. You cannot fetch restaurants.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}