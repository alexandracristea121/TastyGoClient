package com.examples.licenta_food_ordering.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.licenta_food_ordering.R
import com.examples.licenta_food_ordering.model.food.SuggestedFood

class SuggestedFoodAdapter(
    private val suggestedFoodList: List<SuggestedFood>,
    private val onAddToCartClickListener: OnAddToCartClickListener
) : RecyclerView.Adapter<SuggestedFoodAdapter.SuggestedFoodViewHolder>() {

    interface OnAddToCartClickListener {
        fun onAddToCartClicked(food: SuggestedFood)
    }

    class SuggestedFoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val foodImage: ImageView = itemView.findViewById(R.id.foodImage)
        val restaurantName: TextView = itemView.findViewById(R.id.restaurantName)
        val foodName: TextView = itemView.findViewById(R.id.foodName)
        val foodPrice: TextView = itemView.findViewById(R.id.foodPrice)
        val addToCartButton: Button = itemView.findViewById(R.id.addToCartButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestedFoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.suggested_food_item, parent, false)
        return SuggestedFoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestedFoodViewHolder, position: Int) {
        val currentItem = suggestedFoodList[position]
        
        holder.restaurantName.text = currentItem.restaurantName
        holder.foodName.text = currentItem.foodName
        holder.foodPrice.text = currentItem.foodPrice

        Glide.with(holder.itemView.context)
            .load(currentItem.foodImageResId)
            .centerCrop()
            .into(holder.foodImage)

        holder.addToCartButton.setOnClickListener {
            onAddToCartClickListener.onAddToCartClicked(currentItem)
        }
    }

    override fun getItemCount() = suggestedFoodList.size
}