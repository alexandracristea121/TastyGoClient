package com.examples.licenta_food_ordering.utils.userInterface

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import com.example.licenta_food_ordering.R

fun Activity.setupSystemUI() {
    window.statusBarColor = ContextCompat.getColor(this, R.color.white)
    window.navigationBarColor = ContextCompat.getColor(this, R.color.white)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
}