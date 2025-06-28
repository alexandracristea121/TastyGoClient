package com.examples.licenta_food_ordering.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.licenta_food_ordering.databinding.BuyAgainItemBinding
import com.examples.licenta_food_ordering.model.cart.CartItem
import com.examples.licenta_food_ordering.utils.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class BuyItemsAgainAdapter(
    private val foodNames: MutableList<String>,
    private val foodPrices: MutableList<String>,
    private val foodImages: MutableList<String>,
    private val context: Context
) : RecyclerView.Adapter<BuyItemsAgainAdapter.BuyAgainViewHolder>() {

    private var itemClickListener: OnItemClickListener? = null
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuyAgainViewHolder {
        val binding = BuyAgainItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BuyAgainViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BuyAgainViewHolder, position: Int) {
        val validPosition = minOf(foodNames.size, foodPrices.size, foodImages.size)
        if (position < validPosition) {
            holder.bind(
                foodNames[position],
                foodPrices[position],
                foodImages[position]
            )
        }
    }

    override fun getItemCount(): Int {
        return minOf(foodNames.size, foodPrices.size, foodImages.size)
    }

    @Suppress("DEPRECATION")
    inner class BuyAgainViewHolder(private val binding: BuyAgainItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(name: String, price: String, image: String) {
            binding.buyAgainFoodName.text = name
            val rawPrice = price.replace(",", ".")
            val priceDouble = rawPrice.toDoubleOrNull()
            val formattedPrice = if (priceDouble != null) {
                if (priceDouble % 1.0 == 0.0) {
                    "${priceDouble.toInt()} RON"
                } else {
                    "%.2f RON".format(priceDouble)
                }
            } else if (rawPrice.isBlank()) {
                "Indisponibil"
            } else {
                "Indisponibil"
            }
            binding.buyAgainFoodPrice.text = formattedPrice
            ImageUtils.loadImage(context, image, binding.buyAgainFoodImage)

            itemView.setOnClickListener {
                itemClickListener?.onItemClick(adapterPosition)
            }

            binding.buyAgainFoodButton.setOnClickListener {
                addToCart(name, price, image)
            }
        }

        private fun addToCart(name: String, price: String, image: String) {
            val userId = auth.currentUser?.uid ?: return
            val cartItem = CartItem(
                foodName = name,
                foodPrice = price,
                foodDescription = "",
                foodImage = image,
                foodQuantity = 1,
                foodIngredient = ""
            )

            database.reference
                .child("user")
                .child(userId)
                .child("CartItems")
                .push()
                .setValue(cartItem)
                .addOnSuccessListener {
                    Toast.makeText(context, "Item added to cart successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to add item to cart", Toast.LENGTH_SHORT).show()
                }
        }
    }
}