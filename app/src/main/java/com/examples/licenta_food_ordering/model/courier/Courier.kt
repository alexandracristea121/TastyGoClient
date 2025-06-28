package com.examples.licenta_food_ordering.model.courier

data class Courier(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val restaurantLatitude: Double = 0.0,
    val restaurantLongitude: Double = 0.0,
    val userLatitude: Double = 0.0,
    val userLongitude: Double = 0.0,
    val userUid: String = "",
    val orderId: String = "",
    val status: CourierStatus = CourierStatus.AVAILABLE,
    val lastUpdate: Long = System.currentTimeMillis(),
    val minDistanceRaw: Double = 0.0,
    val trafficEstimationInMinutes: Int = 0
)

enum class CourierStatus {
    AVAILABLE,
    DELIVERING
}