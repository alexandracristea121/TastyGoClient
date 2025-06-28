package com.examples.licenta_food_ordering.service.courier.tracking

import android.util.Log
import com.examples.licenta_food_ordering.model.courier.CourierStatus
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

class FirebaseCourierService {
    private val database = FirebaseDatabase.getInstance().reference.child("couriers")

    private val centerLat = 45.7489
    private val centerLng = 21.2087
    private val radiusInMeters = 1000.0

    fun updateCourierLocation(courierId: String, location: LatLng) {
        val locationMap = mapOf("latitude" to location.latitude, "longitude" to location.longitude)
        database.child(courierId).setValue(locationMap)
    }

    fun getCouriersLocations(callback: (List<LatLng>) -> Unit) {
        val couriersRef = FirebaseDatabase.getInstance().getReference("couriers")

        couriersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<LatLng>()

                for (courierSnapshot in snapshot.children) {
                    val lat = courierSnapshot.child("latitude").getValue(Double::class.java)
                    val lng = courierSnapshot.child("longitude").getValue(Double::class.java)

                    if (lat != null && lng != null) {
                        val courierLocation = LatLng(lat, lng)
                        locations.add(courierLocation)
                        Log.d("Firebase", "Curier găsit: ${courierSnapshot.key} - Lat: $lat, Lng: $lng")
                    } else {
                        Log.e("Firebase", "Locație invalidă pentru curier: ${courierSnapshot.key}")
                    }
                }

                callback(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error fetching couriers: ${error.message}")
            }
        })
    }

    fun generateRandomLocationInsideHotspot(): LatLng {
        val randomRadius = Random.nextDouble(0.0, radiusInMeters)
        val randomAngle = Random.nextDouble(0.0, 2 * Math.PI)

        val offsetLat = randomRadius * Math.cos(randomAngle)
        val offsetLng = randomRadius * Math.sin(randomAngle)

        val newLat = centerLat + (offsetLat / 111.32)
        val newLng = centerLng + (offsetLng / (111.32 * Math.cos(Math.toRadians(centerLat))))

        return LatLng(newLat, newLng)
    }

    fun generateAndSaveCouriers(numberOfCouriers: Int) {
        val courierRef = FirebaseDatabase.getInstance().getReference("couriers")

        for (i in 1..numberOfCouriers) {
            val randomLocation = generateRandomLocationInsideHotspot()

            val courierId = "courier_$i"

            val courierData = mapOf(
                "latitude" to randomLocation.latitude,
                "longitude" to randomLocation.longitude,
                "status" to CourierStatus.AVAILABLE.name,
                "lastUpdate" to System.currentTimeMillis()
            )

            courierRef.child(courierId).setValue(courierData).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Firebase", "Curierul $courierId a fost salvat cu succes.")
                } else {
                    Log.e("Firebase", "Eroare la salvarea curierului $courierId.")
                }
            }
        }
    }
}