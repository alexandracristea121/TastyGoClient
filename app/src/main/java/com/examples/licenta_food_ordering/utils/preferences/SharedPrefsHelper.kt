package com.examples.licenta_food_ordering.utils.preferences

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SharedPrefsHelper {

    private const val PREFS_NAME = "food_ordering_prefs"
    private const val KEY_ADMIN_USER_ID = "admin_user_id"
    private const val KEY_RESTAURANT_NAME = "restaurant_name"

    private const val KEY_USER_LAT = "user_lat"
    private const val KEY_USER_LNG = "user_lng"

    private val sessionRestaurantNames = mutableListOf<String>()

    fun saveAdminUserId(context: Context, adminUserId: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_ADMIN_USER_ID, adminUserId)
        editor.apply()
    }

    fun saveRestaurantNames(context: Context, restaurantName: String) {
        if (restaurantName != "Restaurant Name" && !sessionRestaurantNames.contains(restaurantName)) {
            sessionRestaurantNames.add(restaurantName)
        }
    }

    fun resetRestaurantNames(context: Context) {
        sessionRestaurantNames.clear()
    }

    fun saveUserLocation(context: Context, latitude: Double, longitude: Double) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat(KEY_USER_LAT, latitude.toFloat())
        editor.putFloat(KEY_USER_LNG, longitude.toFloat())
        editor.apply()
    }

    fun getUserLocation(context: Context): Pair<Double, Double> {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lat = sharedPreferences.getFloat(KEY_USER_LAT, 0f).toDouble()
        val lng = sharedPreferences.getFloat(KEY_USER_LNG, 0f).toDouble()
        return Pair(lat, lng)
    }

    fun getAdminUserId(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_ADMIN_USER_ID, null)
    }

    fun getRestaurantNames(context: Context): List<String> {
        return sessionRestaurantNames.toList()
    }
}