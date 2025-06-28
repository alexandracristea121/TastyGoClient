package com.examples.licenta_food_ordering.service.courier.map

import android.os.Build
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.GeocodingApi
import com.google.maps.model.TrafficModel
import com.google.maps.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.maps.model.LatLng as GoogleLatLng

class DistanceCalculationUtility {

    private val apiKey = "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"

    private val context: GeoApiContext = GeoApiContext.Builder()
        .apiKey(apiKey)
        .build()

    fun getRealDistance(origin: String, destination: String): Double {
//        return 5.0 // Return 5km as mock distance
        
        val matrix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DirectionsApi.newRequest(context)
                .origin(origin)
                .destination(destination)
                .mode(TravelMode.DRIVING)  // Calculate for driving
                .trafficModel(TrafficModel.PESSIMISTIC)  // Use pessimistic traffic model
                .departureTime(Instant.now())  // Set departure time to now
                .await()
        } else {
            TODO("VERSION.SDK_INT < O")
        } // Wait for the result

        // Return distance in kilometers
        return matrix.routes[0].legs[0].distance.inMeters / 1000.0

    }

    // 🔹 Geocode an address to get its coordinates (latitude, longitude)
    suspend fun getCoordinatesFromAddress(address: String): GmsLatLng = withContext(Dispatchers.IO) {
        // Mock coordinates for testing
        return@withContext GmsLatLng(44.4268, 26.1025) // Example coordinates for Bucharest
        
        try {
            val results = GeocodingApi.geocode(context, address).await()
            if (results.isNotEmpty()) {
                val location: GoogleLatLng = results[0].geometry.location
                return@withContext GmsLatLng(location.lat, location.lng)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext GmsLatLng(0.0, 0.0)
    }
    fun getEstimatedDeliveryTime(
        origin: String,
        destination: String,
        callback: (String?) -> Unit
    ) {
        // Mock implementation for testing
//        callback("30 mins")

        // TODO: uncomment when doing demo
        val apiKey = "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"
        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=${origin}" +
                "&destinations=${destination}" +
                "&key=${apiKey}"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "")
                val rows = json.getJSONArray("rows")
                val elements = rows.getJSONObject(0).getJSONArray("elements")
                val durationObj = elements.getJSONObject(0).getJSONObject("duration")
                val durationText = durationObj.getString("text")
                callback(durationText)
            }
        })
    }

    fun getAddressFromCoordinates(
        latitude: Double,
        longitude: Double,
        callback: (String?) -> Unit
    ) {
        // Mock address for testing
//        callback("Test Address, Bucharest, Romania")
        
        val apiKey = "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"
        val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                "?latlng=${latitude},${longitude}" +
                "&key=${apiKey}"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "")
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val address = results.getJSONObject(0).getString("formatted_address")
                    callback(address)
                } else {
                    callback(null)
                }
            }
        })
    }
}