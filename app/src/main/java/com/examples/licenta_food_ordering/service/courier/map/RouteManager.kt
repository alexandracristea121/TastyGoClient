package com.examples.licenta_food_ordering.service.courier.map

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object RouteManager {

    private const val TAG = "RouteManager"
    const val DIRECTIONS_API_KEY = "AIzaSyCHP4wYR_Qe0d1sasOGQ-vlmCncYK2F4KQ"

    private var polyline: Polyline? = null
    private var isRouteVisible = false

    fun toggleRoute(context: Context, map: GoogleMap, points: List<LatLng>) {
        if (points.size < 2) return

        if (isRouteVisible) {
            polyline?.remove()
            isRouteVisible = false
        } else {
            val origin = "${points.first().latitude},${points.first().longitude}"
            val destination = "${points.last().latitude},${points.last().longitude}"

            val waypoints = points.subList(1, points.size - 1).joinToString("|") { "${it.latitude},${it.longitude}" }

            val url = buildDirectionsUrl(origin, destination, waypoints)

            fetchRoute(url) { polylinePoints ->
                (context as? Activity)?.runOnUiThread {
                    polyline = map.addPolyline(
                        PolylineOptions()
                            .addAll(polylinePoints)
                            .width(8f)
                            .color(Color.BLUE)
                    )
                    isRouteVisible = true
                }
            }
        }
    }

    private fun buildDirectionsUrl(origin: String, destination: String, waypoints: String?): String {
        val baseUrl = "https://maps.googleapis.com/maps/api/directions/json"
        val waypointsParam = if (!waypoints.isNullOrEmpty()) "&waypoints=$waypoints" else ""
        return "$baseUrl?origin=$origin&destination=$destination$waypointsParam&key=$DIRECTIONS_API_KEY"
    }

    private fun fetchRoute(url: String, callback: (List<LatLng>) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Route fetch failed: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.body?.string()?.let { json ->
                    val polylinePoints = parsePolylinePoints(json)

                    callback(polylinePoints)
                }
            }
        })
    }

    private fun parsePolylinePoints(json: String): List<LatLng> {
        val polylinePoints = mutableListOf<LatLng>()
        val jsonObject = JSONObject(json)
        val routes = jsonObject.getJSONArray("routes")
        if (routes.length() > 0) {
            val overviewPolyline = routes.getJSONObject(0)
                .getJSONObject("overview_polyline")
                .getString("points")
            polylinePoints.addAll(decodePolyline(overviewPolyline))
        }
        return polylinePoints
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }

        return poly
    }
}