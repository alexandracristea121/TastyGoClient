package com.examples.licenta_food_ordering.presentation.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.licenta_food_ordering.databinding.ActivityPayOutBinding
import com.examples.licenta_food_ordering.model.order.OrderDetails
import com.examples.licenta_food_ordering.presentation.ui.OrderSuccessBottomSheet
import com.examples.licenta_food_ordering.service.courier.map.DistanceCalculationUtility
import com.examples.licenta_food_ordering.utils.ConfigUtils
import com.examples.licenta_food_ordering.utils.network.RetrofitClient
import com.examples.licenta_food_ordering.utils.payment.PaymentRequest
import com.examples.licenta_food_ordering.utils.preferences.SharedPrefsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.PaymentIntentResult
import com.stripe.android.Stripe
import com.stripe.android.model.CardParams
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.StripeIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PaymentActivity : AppCompatActivity() {

    lateinit var binding: ActivityPayOutBinding

    private lateinit var auth: FirebaseAuth
    private lateinit var name: String
    private lateinit var userLocation: String
    private lateinit var restaurantLocation: String
    private lateinit var phone: String
    private lateinit var totalAmount: String
    private lateinit var foodItemName: ArrayList<String>
    private lateinit var foodItemPrice: ArrayList<String>
    private lateinit var foodItemImage: ArrayList<String>
    private lateinit var foodItemQuantities: ArrayList<Int>
    private lateinit var foodItemDescriptions: ArrayList<String>
    private lateinit var foodItemIngredients: ArrayList<String>
    private lateinit var databaseReference: DatabaseReference
    private lateinit var userId: String
    private lateinit var distanceCalculationUtility: DistanceCalculationUtility
    private lateinit var stripe: Stripe
    private lateinit var adminUserId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPayOutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        distanceCalculationUtility = DistanceCalculationUtility()

        adminUserId = SharedPrefsHelper.getAdminUserId(this) ?: "Unknown Admin ID"

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().getReference()

        ConfigUtils.initialize(applicationContext)

        stripe = Stripe(
            applicationContext,
            ConfigUtils.getStripePublishableKey()
        )

        PaymentConfiguration.init(applicationContext, ConfigUtils.getStripePublishableKey())

        setUserData()

        val intent = intent
        foodItemName = intent.getStringArrayListExtra("FoodItemName") as ArrayList<String>
        foodItemPrice = intent.getStringArrayListExtra("FoodItemPrice") as ArrayList<String>
        foodItemImage = intent.getStringArrayListExtra("FoodItemImage") as ArrayList<String>
        foodItemQuantities = intent.getIntegerArrayListExtra("FoodItemQuantities") as ArrayList<Int>
        foodItemDescriptions = intent.getStringArrayListExtra("FoodItemDescription") ?: arrayListOf()
        foodItemIngredients = intent.getStringArrayListExtra("FoodItemIngredients") ?: arrayListOf()

        val restaurantName = SharedPrefsHelper.getRestaurantNames(this).firstOrNull() ?: "Unknown Restaurant Location"
        fetchRestaurantLocation(restaurantName) { location ->
            restaurantLocation = location ?: "Unknown Restaurant Location"
        }

        totalAmount = calculateTotalAmount().toString() + " RON"
        binding.totalAmount.setText(totalAmount)

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.placeMyOrder.setOnClickListener {
            name = binding.name.text.toString().trim()
            userLocation = binding.address.text.toString().trim()
            phone = binding.phone.text.toString().trim()

            if (name.isBlank() || userLocation.isBlank() || phone.isBlank()) {
                Toast.makeText(this, "Please enter all the details", Toast.LENGTH_SHORT).show()
            } else {
                val amount = calculateTotalAmountInCents()
                createPaymentIntent(amount)
            }
        }
    }

    private fun createPaymentIntent(amount: Long) {
        val paymentRequest = PaymentRequest(amount)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.createPaymentIntent(paymentRequest)
                if (response.isSuccessful) {
                    val clientSecret = response.body()?.clientSecret
                    if (clientSecret != null) {
                        withContext(Dispatchers.Main) {
                            confirmPayment(clientSecret)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PaymentActivity, "Failed to get client secret", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PaymentActivity, "Failed to create payment intent", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PaymentActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmPayment(clientSecret: String) {
        val cardParams = PaymentMethodCreateParams.createCard(
            CardParams("4242424242424242", 12, 25, "123")
        )

        val confirmParams = ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
            cardParams,
            clientSecret
        )

        stripe.confirmPayment(this, confirmParams)
    }

    private fun calculateTotalAmount(): Int {
        var totalAmount = 0
        for (i in foodItemPrice.indices) {
            val price = foodItemPrice[i]

            val priceWithoutCurrency = when {
                price.startsWith("$") -> price.drop(1)
                price.startsWith("€") -> price.drop(1)
                price.endsWith("$") -> price.dropLast(1)
                price.endsWith("€") -> price.dropLast(1)
                else -> price
            }

            val normalizedPrice = priceWithoutCurrency.replace(",", ".").toDouble()
            val priceIntValue = normalizedPrice.toInt()
            val quantity = foodItemQuantities[i]
            totalAmount += priceIntValue * quantity
        }
        val deliveryFee = 10
        totalAmount += deliveryFee
        return totalAmount
    }

    private fun calculateTotalAmountInCents(): Long {
        var totalAmount = 0L
        for (i in foodItemPrice.indices) {
            val price = foodItemPrice[i]

            val priceInCents = when {
                price.startsWith("$") -> {
                    val normalizedPrice = price.drop(1).replace(",", ".").toDouble()
                    (normalizedPrice * 100).toLong()
                }
                price.startsWith("€") -> {
                    val normalizedPrice = price.drop(1).replace(",", ".").toDouble()
                    (normalizedPrice * 100).toLong()
                }
                price.endsWith("$") -> {
                    val normalizedPrice = price.dropLast(1).replace(",", ".").toDouble()
                    (normalizedPrice * 100).toLong()
                }
                price.endsWith("€") -> {
                    val normalizedPrice = price.dropLast(1).replace(",", ".").toDouble()
                    (normalizedPrice * 100).toLong()
                }
                else -> {
                    val normalizedPrice = if (price.contains(",")) {
                        price.replace(",", ".").toDouble()
                    } else {
                        price.toDouble()
                    }
                    (normalizedPrice * 100).toLong()
                }
            }

            val quantity = foodItemQuantities[i]
            totalAmount += priceInCents * quantity
        }
        val deliveryFeeInCents = 1000L
        totalAmount += deliveryFeeInCents
        return totalAmount
    }

    private fun setUserData() {
        val user = auth.currentUser
        if (user != null) {
            val userId = user.uid
            val userReference = databaseReference.child("user").child(userId)
            userReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val names = snapshot.child("name").getValue(String::class.java) ?: ""
                        val addresses = snapshot.child("address").getValue(String::class.java) ?: ""
                        val phones = snapshot.child("phone").getValue(String::class.java) ?: ""
                        binding.apply {
                            name.setText(names)
                            address.setText(addresses)
                            phone.setText(phones)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        stripe.onPaymentResult(requestCode, data, object : ApiResultCallback<PaymentIntentResult> {
            override fun onSuccess(result: PaymentIntentResult) {
                result.intent.status?.let { status ->
                    when (status) {
                        StripeIntent.Status.Succeeded -> {
                            Toast.makeText(this@PaymentActivity, "Payment successful", Toast.LENGTH_SHORT).show()
                            placeOrder()
                        }
                        StripeIntent.Status.RequiresPaymentMethod -> {
                            Toast.makeText(this@PaymentActivity, "Payment failed", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(this@PaymentActivity, "Payment status unknown", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    Toast.makeText(this@PaymentActivity, "Payment status is null", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(e: Exception) {
                Toast.makeText(this@PaymentActivity, "Payment failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun placeOrder() {
        val restaurantNames = SharedPrefsHelper.getRestaurantNames(this)

        if (restaurantNames.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.Main) {
                val deferredLocations = restaurantNames.map { async { 
                    val coordinates = fetchRestaurantCoordinates(it)
                    "${coordinates.first},${coordinates.second}"
                } }
                val restaurantLocations = deferredLocations.awaitAll().filterNotNull()
                if (restaurantLocations.isNotEmpty()) {
                    assignCourierAndCalculateTime(
                        restaurantAddress = restaurantLocations.first(),
                        userAddress = userLocation
                    )
                } else {
                    Toast.makeText(this@PaymentActivity, "Restaurant locations not found", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Restaurant name list is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun assignCourierAndCalculateTime(restaurantAddress: String, userAddress: String) {
        databaseReference.child("couriers")
            .orderByChild("status").equalTo("AVAILABLE")
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val courierSnapshot = snapshot.children.first()
                    val courierId = courierSnapshot.key
                    val latitudeStr = courierSnapshot.child("latitude").getValue(Double::class.java)
                    val longitudeStr = courierSnapshot.child("longitude").getValue(Double::class.java)

                    if (latitudeStr != null && longitudeStr != null) {
                        distanceCalculationUtility.getEstimatedDeliveryTime(
                            restaurantAddress,
                            userAddress
                        ) { estimatedTime ->
                            if (estimatedTime != null) {
                                if (courierId != null) {
                                    createOrderWithCourier(courierId, estimatedTime)
                                } else {
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@PaymentActivity,
                                            "Failed to get courier ID",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@PaymentActivity,
                                        "Failed to calculate delivery time",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Courier location not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No available courier found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to query couriers", Toast.LENGTH_SHORT).show()
            }
    }

    @SuppressLint("SimpleDateFormat")
    private fun createOrderWithCourier(courierId: String, estimatedTime: String) {
        lifecycleScope.launch {
            val itemPushKey = databaseReference.child("orders").push().key
            val currentDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())

            val currentUser = auth.currentUser
            userId = currentUser?.uid ?: "Unknown User ID"
            
            val userCoordinates = suspendCoroutine<Pair<Double, Double>> { continuation ->
                databaseReference.child("user").child(userId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val userAddress = snapshot.child("address").getValue(String::class.java)
                        if (userAddress != null) {
                            val geocoder = Geocoder(this@PaymentActivity, Locale.getDefault())
                            try {
                                val addresses = geocoder.getFromLocationName(userAddress, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val location = addresses[0]
                                    continuation.resume(Pair(location.latitude, location.longitude))
                                } else {
                                    continuation.resume(Pair(0.0, 0.0))
                                }
                            } catch (e: Exception) {
                                continuation.resume(Pair(0.0, 0.0))
                            }
                        } else {
                            continuation.resume(Pair(0.0, 0.0))
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(Pair(0.0, 0.0))
                    }
            }

            val restaurantName = SharedPrefsHelper.getRestaurantNames(this@PaymentActivity).firstOrNull()
            val restaurantCoordinates = if (restaurantName != null) {
                fetchRestaurantCoordinates(restaurantName)
            } else {
                Pair(0.0, 0.0)
            }

            val orderDetails = OrderDetails(
                userId,
                name,
                foodItemName,
                foodItemPrice,
                foodItemImage,
                foodItemQuantities,
                foodItemDescriptions,
                foodItemIngredients,
                userLocation,
                userCoordinates.first.toString(),
                userCoordinates.second.toString(),
                "${restaurantCoordinates.first},${restaurantCoordinates.second}",
                totalAmount,
                phone,
                currentDate,
                itemPushKey,
                orderAccepted = false,
                paymentReceived = false,
                adminUserId = adminUserId,
                restaurantName = restaurantName ?: "Unknown Restaurant",
                courierId = courierId,
                estimatedDeliveryTime = estimatedTime
            )

            databaseReference.child("orders").child(itemPushKey!!).setValue(orderDetails)
                .addOnSuccessListener {
                    databaseReference.child("couriers").child(courierId).child("status").setValue("DELIVERING")
                    databaseReference.child("couriers").child(courierId).child("orderId").setValue(itemPushKey)
                    val bottomSheetDialog = OrderSuccessBottomSheet()
                    bottomSheetDialog.show(supportFragmentManager, "Test")
                    removeItemFromCart()
                    addOrderToHistory(orderDetails)
                }
                .addOnFailureListener {
                    Toast.makeText(this@PaymentActivity, "Failed to place order", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private suspend fun fetchRestaurantCoordinates(restaurantName: String): Pair<Double, Double> {
        return suspendCoroutine { continuation ->
            databaseReference.child("Restaurants")
                .orderByChild("name")
                .equalTo(restaurantName)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val restaurant = snapshot.children.firstOrNull()
                        val latitude = restaurant?.child("latitude")?.getValue(Double::class.java) ?: 0.0
                        val longitude = restaurant?.child("longitude")?.getValue(Double::class.java) ?: 0.0
                        continuation.resume(Pair(latitude, longitude))
                    } else {
                        continuation.resume(Pair(0.0, 0.0))
                    }
                }
                .addOnFailureListener {
                    continuation.resume(Pair(0.0, 0.0))
                }
        }
    }

    private fun fetchRestaurantLocation(restaurantName: String, callback: (String?) -> Unit) {
        databaseReference.child("Restaurants")
            .orderByChild("name")
            .equalTo(restaurantName)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val location = snapshot.children.firstOrNull()?.child("address")?.value.toString()
                    callback(location)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun removeItemFromCart() {
        val cartItemsReference = databaseReference.child("user").child(userId).child("CartItems")
        cartItemsReference.removeValue()
    }

    private fun addOrderToHistory(orderDetails: OrderDetails) {
        databaseReference.child("user").child(userId).child("BuyHistory")
            .child(orderDetails.itemPushkey!!).setValue(orderDetails)
            .addOnSuccessListener {}
    }
}