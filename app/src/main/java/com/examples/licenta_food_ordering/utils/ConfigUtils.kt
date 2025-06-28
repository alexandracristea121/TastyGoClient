package com.examples.licenta_food_ordering.utils

import android.content.Context
import java.util.Properties

object ConfigUtils {
    private var properties: Properties? = null

    fun initialize(context: Context) {
        if (properties == null) {
            properties = Properties().apply {
                context.assets.open("stripe.properties").use { input ->
                    load(input)
                }
            }
        }
    }

    fun getStripePublishableKey(): String {
        return properties?.getProperty("STRIPE_PUBLISHABLE_KEY") ?: ""
    }

    fun getGooglePlacesApiKey(): String {
        return properties?.getProperty("GOOGLE_PLACES_API_KEY") ?: ""
    }
} 