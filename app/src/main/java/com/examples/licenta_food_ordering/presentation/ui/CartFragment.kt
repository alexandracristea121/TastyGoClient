package com.examples.licenta_food_ordering.presentation.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.licenta_food_ordering.databinding.FragmentCartBinding
import com.examples.licenta_food_ordering.presentation.activity.PaymentActivity
import com.examples.licenta_food_ordering.adapter.CartAdapter
import com.examples.licenta_food_ordering.model.cart.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.NumberFormat
import java.util.Locale

class CartFragment : Fragment() {
    private lateinit var binding: FragmentCartBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var cartItems: MutableList<String>
    private lateinit var cartItemPrices: MutableList<String>
    private lateinit var cartDescriptions: MutableList<String>
    private lateinit var cartImages: MutableList<String>
    private lateinit var cartQuantity: MutableList<Int>
    private lateinit var cartIngredient: MutableList<String>
    private lateinit var cartAdapter: CartAdapter
    private lateinit var userId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCartBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        retrieveCartItems()

        binding.proceedButton.setOnClickListener {
            getOrderItemsDetails()
        }

        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun updateCartTotals() {
        var totalItems = 0
        var totalPrice = 0.0

        for (i in cartItemPrices.indices) {
            val priceStr = cartItemPrices[i].replace(",", ".").replace("$", "").trim()
            val price = priceStr.toDoubleOrNull() ?: 0.0
            val itemQuantity = cartQuantity[i]
            totalItems += itemQuantity
            totalPrice += price * itemQuantity
        }

        val deliveryFee = 10.0
        val formattedDeliveryFee = String.format("Taxa Livrare: %.0f RON", deliveryFee)
        binding.deliveryFeeText.text = formattedDeliveryFee

        val totalWithDelivery = totalPrice + deliveryFee
        val formattedPrice = String.format("%.2f RON", totalWithDelivery)

        binding.totalItemsText.text = "Total Items: $totalItems"
        binding.totalPriceText.text = "Total: $formattedPrice"
    }

    private fun getOrderItemsDetails() {
        userId = auth.currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("user").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cardNumber = snapshot.child("cardNumber").getValue(String::class.java)
                val expiryDate = snapshot.child("expiryDate").getValue(String::class.java)
                val cvv = snapshot.child("cvv").getValue(String::class.java)
                val cardHolderName = snapshot.child("cardHolderName").getValue(String::class.java)

                if (cardNumber.isNullOrBlank() || expiryDate.isNullOrBlank() || cvv.isNullOrBlank() || cardHolderName.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Please complete your card details in your Profile before proceeding to payment.", Toast.LENGTH_LONG).show()
                    return
                }

                retrieveOrderItems()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to check payment info", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun retrieveOrderItems() {
        val orderIdReference: DatabaseReference = database.reference.child("user").child(userId).child("CartItems")

        val foodName = mutableListOf<String>()
        val foodPrice = mutableListOf<String>()
        val foodImage = mutableListOf<String>()
        val foodDescription = mutableListOf<String>()
        val foodIngredient = mutableListOf<String>()

        val foodQuantities = cartAdapter.getUpdatedItemsQuantities()

        orderIdReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (foodSnapshot in snapshot.children) {
                    val orderItems = foodSnapshot.getValue(CartItem::class.java)
                    orderItems?.foodName?.let { foodName.add(it) }
                    orderItems?.foodPrice?.let { foodPrice.add(it) }
                    orderItems?.foodDescription?.let { foodDescription.add(it) }
                    orderItems?.foodImage?.let { foodImage.add(it) }
                    orderItems?.foodIngredient?.let { foodIngredient.add(it) }
                }
                orderNow(foodName, foodPrice, foodDescription, foodImage, foodIngredient, foodQuantities)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Order making failed. Please try again", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun orderNow(
        foodName: MutableList<String>,
        foodPrice: MutableList<String>,
        foodDescription: MutableList<String>,
        foodImage: MutableList<String>,
        foodIngredient: MutableList<String>,
        foodQuantities: MutableList<Int>
    ) {
        if (isAdded && context != null) {
            val intent = Intent(requireContext(), PaymentActivity::class.java)
            intent.putExtra("FoodItemName", foodName as ArrayList<String>)
            intent.putExtra("FoodItemPrice", foodPrice as ArrayList<String>)
            intent.putExtra("FoodItemImage", foodImage as ArrayList<String>)
            intent.putExtra("FoodItemDescription", foodDescription as ArrayList<String>)
            intent.putExtra("FoodItemIngredient", foodIngredient as ArrayList<String>)
            intent.putExtra("FoodItemQuantities", foodQuantities as ArrayList<Int>)
            startActivity(intent)
        }
    }

    private fun retrieveCartItems() {
        database = FirebaseDatabase.getInstance()
        userId = auth.currentUser?.uid ?: ""
        val foodReference: DatabaseReference = database.reference.child("user").child(userId).child("CartItems")
        cartItems = mutableListOf()
        cartItemPrices = mutableListOf()
        cartDescriptions = mutableListOf()
        cartImages = mutableListOf()
        cartQuantity = mutableListOf()
        cartIngredient = mutableListOf()

        foodReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (foodSnapshot in snapshot.children) {
                    val cartItem = foodSnapshot.getValue(CartItem::class.java)

                    cartItem?.foodName?.let { cartItems.add(it) }
                    cartItem?.foodPrice?.let { cartItemPrices.add(it) }
                    cartItem?.foodDescription?.let { cartDescriptions.add(it) }
                    cartItem?.foodImage?.let { cartImages.add(it) }
                    cartItem?.foodQuantity?.let { cartQuantity.add(it) }
                    cartItem?.foodIngredient?.let { cartIngredient.add(it) }
                }

                setAdapter()
                updateCartTotals()
            }

            private fun setAdapter() {
                cartAdapter = CartAdapter(
                    requireContext(),
                    cartItems,
                    cartItemPrices,
                    cartDescriptions,
                    cartImages,
                    cartQuantity,
                    cartIngredient
                )
                binding.cartRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
                binding.cartRecyclerView.adapter = cartAdapter

                cartAdapter.setOnQuantityChangeListener {
                    updateCartTotals()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "data not fetch", Toast.LENGTH_SHORT).show()
            }
        })
    }
}