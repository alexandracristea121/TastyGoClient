package com.examples.licenta_food_ordering.presentation.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.licenta_food_ordering.databinding.ActivityOrderDetailsBinding
import com.examples.licenta_food_ordering.model.order.OrderDetails
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

@Suppress("DEPRECATION")
class OrderDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var ordersRef: DatabaseReference

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = getColor(android.R.color.white)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        database = FirebaseDatabase.getInstance()
        ordersRef = database.getReference("orders")

        val orderId = intent.getStringExtra("orderId")
        if (orderId.isNullOrEmpty()) {
            Toast.makeText(this, "No order ID provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupBackButton()
        fetchOrderDetails(orderId)
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            onBackPressed()
        }
    }

    private fun fetchOrderDetails(orderId: String) {
        ordersRef.child(orderId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val order = snapshot.getValue(OrderDetails::class.java)
                if (order != null) {
                    val foodItems = order.foodNames?.joinToString("\n") ?: "No items"

                    val status = buildString {
                        append(when {
                            order.orderDelivered -> "Delivered"
                            order.orderAccepted -> "Accepted"
                            else -> "Pending"
                        })
                        if (order.paymentReceived) {
                            append(" (Paid)")
                        }
                    }

                    val restaurantAddress = order.restaurantLocation
                    if (!restaurantAddress.isNullOrBlank()) {
                        val ref = FirebaseDatabase.getInstance().getReference("Restaurants")
                        ref.orderByChild("address").equalTo(restaurantAddress)
                            .get().addOnSuccessListener { restSnap ->
                                val name = restSnap.children.firstOrNull()?.child("name")?.getValue(String::class.java)
                                binding.tvUserName.text = name ?: "N/A"
                            }.addOnFailureListener {
                                binding.tvUserName.text = "N/A"
                            }
                    } else {
                        binding.tvUserName.text = "N/A"
                    }

                    binding.apply {
                        tvUserLocation.text = order.userLocation ?: "N/A"
                        tvRestaurantLocation.text = order.restaurantLocation ?: "N/A"
                        tvFoodNames.text = foodItems
                        tvTotalPrice.text = order.totalPrice?.replace("$", "RON") ?: "0.00"
                        tvPhoneNumber.text = order.phoneNumber ?: "N/A"
                        tvOrderStatus.text = status
                        tvCurrentTime.text = buildString {
                            append(order.orderTime ?: "N/A")
                            if (order.estimatedDeliveryTime != null) {
                                append("\nTimp estimativ de livrare: ${order.estimatedDeliveryTime}")
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error fetching order details", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}