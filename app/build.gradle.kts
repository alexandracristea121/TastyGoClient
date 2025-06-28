plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.androidx.navigation.safe.args)
}

android {
    namespace = "com.example.licenta_food_ordering"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.licenta_food_ordering"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildToolsVersion = "34.0.0"
}

dependencies {
    // AndroidX and support libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("org.jsoup:jsoup:1.15.3")

    // Firebase dependencies
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)

    // Networking and other utilities
    implementation(libs.car.ui.lib)
    implementation(libs.volley)
    implementation(libs.androidx.tools.core)
    implementation(libs.play.services.wallet)
    implementation(libs.androidx.espresso.core)
    implementation("com.google.android.libraries.places:places:3.0.0")

    // Testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Image Slideshow library
    implementation("com.github.denzcoskun:ImageSlideshow:0.1.0")

    // Google Play services dependencies
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Google Maps SDK for Android
    implementation("com.google.android.gms:play-services-maps:18.0.2")
    implementation ("com.google.maps:google-maps-services:0.2.0")
    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("com.google.android.gms:play-services-identity:18.1.0")

    // Stripe
    implementation ("com.stripe:stripe-android:20.24.1")

    // Retrofit
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.1")
}