package com.examples.licenta_food_ordering.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.licenta_food_ordering.databinding.CartItemBinding
import com.examples.licenta_food_ordering.utils.firebase.FirebaseUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class CartAdapter(
    private val context: Context,
    private val cartItems: MutableList<String>,
    private val cartItemPrices: MutableList<String>,
    private val cartDescriptions: MutableList<String>,
    private val cartImages: MutableList<String>,
    private val cartQuantity: MutableList<Int>,
    private val cartIngredient: MutableList<String>
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val cartItemsReference: DatabaseReference
    private var itemQuantities: IntArray
    private var onQuantityChangeListener: (() -> Unit)? = null

    init {
        val userId = auth.currentUser?.uid.orEmpty()
        cartItemsReference = FirebaseDatabase.getInstance().reference
            .child("user").child(userId).child("CartItems")
        itemQuantities = IntArray(cartItems.size) { 1 }
    }

    fun setOnQuantityChangeListener(listener: () -> Unit) {
        onQuantityChangeListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = CartItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = cartItems.size

    fun getUpdatedItemsQuantities(): MutableList<Int> = cartQuantity.toMutableList()

    inner class CartViewHolder(private val binding: CartItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            binding.apply {
                val quantity = itemQuantities[position]
                cartFoodName.text = cartItems[position]
                val rawPrice = cartItemPrices[position].replace(",", ".")
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
                cartItemPrice.text = formattedPrice
                cartItemQuantity.text = quantity.toString()

                Glide.with(context)
                    .load(Uri.parse(cartImages[position]))
                    .into(cartImage)

                minusbutton.setOnClickListener { updateQuantity(position, -1) }
                plusbutton.setOnClickListener { updateQuantity(position, 1) }
                deleteButton.setOnClickListener { attemptDeleteItem(position) }
            }
        }

        private fun updateQuantity(position: Int, delta: Int) {
            val newQuantity = itemQuantities[position] + delta
            if (newQuantity in 1..10) {
                itemQuantities[position] = newQuantity
                cartQuantity[position] = newQuantity
                binding.cartItemQuantity.text = newQuantity.toString()
                onQuantityChangeListener?.invoke()
            }
        }

        private fun attemptDeleteItem(position: Int) {
            if (position != RecyclerView.NO_POSITION) {
                FirebaseUtils.getUniqueKeyAtPosition(cartItemsReference, position) { uniqueKey ->
                    if (uniqueKey != null) {
                        removeItem(position, uniqueKey)
                    } else {
                        Toast.makeText(context, "Item not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private fun removeItem(position: Int, uniqueKey: String) {
            FirebaseUtils.removeItemFromDatabase(
                cartItemsReference,
                uniqueKey,
                onSuccess = {
                    safelyRemoveFromLists(position)
                    notifyItemRemoved(position)
                    if (cartItems.isEmpty()) {
                        notifyDataSetChanged()
                    } else {
                        notifyItemRangeChanged(position, cartItems.size)
                    }
                    Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                    onQuantityChangeListener?.invoke()
                },
                onFailure = { errorMessage ->
                    Toast.makeText(context, "Failed to delete item: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            )
        }

        private fun safelyRemoveFromLists(position: Int) {
            listOf(
                cartItems,
                cartImages,
                cartDescriptions,
                cartQuantity,
                cartItemPrices,
                cartIngredient
            ).forEach { if (position < it.size) it.removeAt(position) }

            if (position < itemQuantities.size) {
                val updatedQuantities = itemQuantities.toMutableList()
                updatedQuantities.removeAt(position)
                itemQuantities = updatedQuantities.toIntArray()
            }
        }
    }
}