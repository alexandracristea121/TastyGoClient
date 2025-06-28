package com.examples.licenta_food_ordering.utils.delivery

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import com.google.gson.Gson
import org.json.JSONObject
import java.io.IOException

object DeliveryUtils {
    data class DistanceMatrixResponse(val rows: List<Row>)
    data class Row(val elements: List<Element>)
    data class Element(val duration: Duration)
    data class Duration(val text: String)

    private const val API_KEY =
        "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A"

    fun calculateDeliveryTime(
        userLat: Double,
        userLng: Double,
        restaurantLat: Double,
        restaurantLng: Double,
        callback: (String) -> Unit
    ) {
        // Mock implementation for testing
//        callback("30 mins")

        // TODO: uncomment when doing demo
        val client = OkHttpClient()
        val origins = "$userLat,$userLng"
        val destinations = "$restaurantLat,$restaurantLng"
        val url =
            "https://maps.googleapis.com/maps/api/distancematrix/json?origins=$origins&destinations=$destinations&key=$API_KEY"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Unavailable")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body?.string()
                    val distanceMatrixResponse =
                        Gson().fromJson(jsonResponse, DistanceMatrixResponse::class.java)
                    val duration =
                        distanceMatrixResponse?.rows?.firstOrNull()?.elements?.firstOrNull()?.duration?.text
                    callback(duration ?: "Unavailable")
                } else {
                    callback("Unavailable")
                }
            }
        })
    }

    fun getUserLocationCoordinates(callback: (Double, Double) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            callback(0.0, 0.0)
            return
        }

        val userRef = FirebaseDatabase.getInstance().getReference("user").child(currentUserId)

        userRef.child("userLocation").get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userLocation = task.result?.getValue(String::class.java)

                if (!userLocation.isNullOrEmpty()) {
                    // Mock coordinates for testing
                    callback(44.4268, 26.1025) // Example coordinates for Bucharest
                } else {
                    callback(0.0, 0.0)
                }
            } else {
                callback(0.0, 0.0)
            }
        }
    }

    fun getCoordinatesFromAddress(
        address: String,
        callback: (Double, Double) -> Unit
    ) {
        // Mock coordinates for testing
//        callback(44.4268, 26.1025) // Example coordinates for Bucharest
        
        val client = OkHttpClient()
        val url = "https://maps.googleapis.com/maps/api/geocode/json?address=${address.replace(" ", "+")}&key=$API_KEY"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(0.0, 0.0)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body?.string()
                    val jsonObject = JSONObject(jsonResponse)
                    val results = jsonObject.getJSONArray("results")
                    if (results.length() > 0) {
                        val location = results.getJSONObject(0).getJSONObject("geometry").getJSONObject("location")
                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")
                        callback(lat, lng)
                    } else {
                        callback(0.0, 0.0)
                    }
                } else {
                    callback(0.0, 0.0)
                }
            }
        })
    }
}