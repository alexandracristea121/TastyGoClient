package com.examples.licenta_food_ordering.utils

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

object ImageUtils {
    fun loadImage(context: Context, imageUrl: String, imageView: android.widget.ImageView) {
        val uri = Uri.parse(imageUrl)
        Glide.with(context)
            .load(uri)
            .apply(RequestOptions().placeholder(android.R.drawable.progress_indeterminate_horizontal))
            .into(imageView)
    }
}