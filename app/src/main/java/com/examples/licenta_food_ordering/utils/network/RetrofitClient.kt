package com.examples.licenta_food_ordering.utils.network

import com.examples.licenta_food_ordering.utils.payment.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient {

    companion object {
        private val retrofit by lazy {
            Retrofit.Builder()
                .baseUrl("https://383b-84-232-135-100.ngrok-free.app") // TODO: change all the time
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient.Builder().addInterceptor(LoggingInterceptor()).build()
                )
                .build()
        }

        val apiService: ApiService by lazy {
            retrofit.create(ApiService::class.java)
        }
    }
}

fun LoggingInterceptor(): HttpLoggingInterceptor {
    val loggingInterceptor = HttpLoggingInterceptor()
    loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

    return loggingInterceptor
}