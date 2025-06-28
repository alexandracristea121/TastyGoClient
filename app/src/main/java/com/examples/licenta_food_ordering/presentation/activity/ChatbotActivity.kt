package com.examples.licenta_food_ordering.presentation.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.AiChatActivityBinding
import com.examples.licenta_food_ordering.adapter.ChatAdapter
import com.examples.licenta_food_ordering.model.chat.Message
import com.examples.licenta_food_ordering.model.order.OrderDetails
import com.examples.licenta_food_ordering.service.courier.map.DistanceCalculationUtility
import com.examples.licenta_food_ordering.utils.delivery.DeliveryUtils
import com.examples.licenta_food_ordering.utils.network.GeminiApiHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION", "LABEL_NAME_CLASH")
class ChatbotActivity : AppCompatActivity() {

    private lateinit var binding: AiChatActivityBinding
    private val messageList = mutableListOf<Message>()
    private lateinit var chatAdapter: ChatAdapter
    private var sugestiiCurente: List<String>? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: FirebaseDatabase
    private lateinit var restaurantsRef: DatabaseReference
    private val distanceCalculationUtility = DistanceCalculationUtility()
    private var userLat: Double = 0.0
    private var userLng: Double = 0.0
    private var currentRestaurantMenus: Map<String, List<MenuItem>> = mapOf()
    private var currentRestaurantName: String? = null
    private var currentMenuItems: List<MenuItem>? = null
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.get(0)?.let { spokenText ->
                val processedText = processSpokenText(spokenText)
                binding.messageEditText.setText(processedText)
                addMessageToChat(processedText, Message.Type.USER)
                sendToGemini(processedText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = getColor(R.color.white)
        window.navigationBarColor = getColor(R.color.white)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        binding = AiChatActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.chatToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.chatToolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        database = FirebaseDatabase.getInstance()
        restaurantsRef = database.getReference("Restaurants")

        setupRecyclerView()

        binding.sendButton.setOnClickListener {
            val userMessage = binding.messageEditText.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                addMessageToChat(userMessage, Message.Type.USER)
                binding.messageEditText.text?.clear()
                sendToGemini(userMessage)
            }
        }

        binding.micButton.setOnClickListener {
            if (checkAudioPermission()) {
                startVoiceRecognition()
            } else {
                requestAudioPermission()
            }
        }

        getUserLocation()
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            AUDIO_PERMISSION_REQUEST_CODE
        )
    }

    private fun processSpokenText(spokenText: String): String {
        val text = spokenText.lowercase()
        
        val patterns = mapOf(
            Regex("(.+?)\\s+la\\s+maxim\\s+(\\d+)\\s*ron", RegexOption.IGNORE_CASE) to "$1 cu valoarea de maxim $2 ron",
            Regex("(.+?)\\s+maxim\\s+(\\d+)\\s*ron", RegexOption.IGNORE_CASE) to "$1 cu valoarea de maxim $2 ron",
            Regex("(.+?)\\s+sub\\s+(\\d+)\\s*ron", RegexOption.IGNORE_CASE) to "$1 cu valoarea de maxim $2 ron",
            Regex("meniu\\s*(\\d+)", RegexOption.IGNORE_CASE) to "meniu $1",
            Regex("^(\\d+)$") to "$1",
            Regex("comanda\\s*(\\d+)", RegexOption.IGNORE_CASE) to "$1",
            Regex("comanda\\s+opțiunea\\s+(\\d+)", RegexOption.IGNORE_CASE) to "$1",
            Regex("opțiunea\\s+(\\d+)", RegexOption.IGNORE_CASE) to "$1",
            Regex("aleg\\s+opțiunea\\s+(\\d+)", RegexOption.IGNORE_CASE) to "$1",
            Regex("vreau\\s+opțiunea\\s+(\\d+)", RegexOption.IGNORE_CASE) to "$1",
            Regex("doresc\\s+opțiunea\\s+(\\d+)", RegexOption.IGNORE_CASE) to "$1"
        )

        for ((pattern, replacement) in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                if (replacement == "$1") {
                    return match.groupValues[1]
                }
                return text.replace(pattern, replacement)
            }
        }

        return spokenText
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
            
            val prompt = when {
                sugestiiCurente != null -> "Spune numărul opțiunii dorite (ex: 'Comanda opțiunea 1' sau 'Vreau opțiunea 2') sau 'meniu' urmat de numărul restaurantului"
                currentMenuItems != null -> "Spune 'comanda' urmat de numărul produsului dorit (ex: 'Comanda produsul 1')"
                else -> "Spune comanda ta (ex: Pizza la maxim 35 RON)"
            }
            
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare la pornirea recunoașterii vocale", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                Toast.makeText(
                    this,
                    "Permisiunea pentru microfon este necesară pentru comenzi vocale",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1001
    }

    private fun setupRecyclerView() {
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messageList)
        binding.chatRecyclerView.adapter = chatAdapter
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    userLat = location.latitude
                    userLng = location.longitude
                }
            }
    }

    private fun addMessageToChat(message: String, type: Message.Type) {
        messageList.add(Message(message, type))
        chatAdapter.notifyItemInserted(messageList.size - 1)
        binding.chatRecyclerView.scrollToPosition(messageList.size - 1)
    }

    private fun sendToGemini(userMessage: String) {
        binding.progressBar.visibility = View.VISIBLE

        val menuRegex = Regex("meniu\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val menuMatch = menuRegex.find(userMessage)
        if (menuMatch != null && sugestiiCurente != null) {
            val restaurantNumber = menuMatch.groupValues[1].toIntOrNull()
            if (restaurantNumber != null && restaurantNumber in 1..sugestiiCurente!!.size) {
                showRestaurantMenu(restaurantNumber)
                return
            }
        }

        val orderRegex = Regex("comanda\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val orderMatch = orderRegex.find(userMessage)
        if (orderMatch != null && currentMenuItems != null) {
            val itemNumber = orderMatch.groupValues[1].toIntOrNull()
            if (itemNumber != null && itemNumber in 1..currentMenuItems!!.size) {
                handleMenuOrderSelection(itemNumber)
                return
            }
        }

        val alegere = userMessage.toIntOrNull()
        if (alegere != null && sugestiiCurente != null && alegere in 1..sugestiiCurente!!.size) {
            handleOrderSelection(alegere)
            return
        }

        handleFoodSearch(userMessage)
    }

    private fun showRestaurantMenu(restaurantNumber: Int) {
        val suggestion = sugestiiCurente!![restaurantNumber - 1]
        val restaurantName = suggestion.substringAfter("de la restaurantul: ").substringBefore(".")
        
        val menuItems = currentRestaurantMenus[restaurantName]
        if (menuItems != null) {
            currentRestaurantName = restaurantName
            currentMenuItems = menuItems
            val menuText = buildString {
                append("📋 Meniul complet al restaurantului **$restaurantName**:\n\n")
                menuItems.forEachIndexed { index, item ->
                    append("${index + 1}. ${item.foodName} - ${item.foodPrice} RON\n")
                    if (item.foodDescription.isNotEmpty()) {
                        append("   _${item.foodDescription}_\n")
                    }
                    if (item.foodIngredients.isNotEmpty()) {
                        append("   Ingrediente: ${item.foodIngredients}\n")
                    }
                    append("\n")
                }
                append("\n✋ Pentru a comanda, răspunde cu *comanda* urmat de numărul produsului (ex: comanda 1).")
            }
            binding.progressBar.visibility = View.GONE
            addMessageToChat(menuText, Message.Type.BOT)
        } else {
            binding.progressBar.visibility = View.GONE
            addMessageToChat("Nu am putut găsi meniul restaurantului.", Message.Type.BOT)
        }
    }

    private fun handleMenuOrderSelection(itemNumber: Int) {
        val menuItem = currentMenuItems!![itemNumber - 1]
        val restaurantName = currentRestaurantName!!
        
        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userUid = sharedPref.getString("userId", null)

        val userRef = FirebaseDatabase.getInstance().getReference("user").child(userUid!!)
        userRef.get().addOnSuccessListener { userSnapshot ->
            val cardNumber = userSnapshot.child("cardNumber").getValue(String::class.java)
            val expiryDate = userSnapshot.child("expiryDate").getValue(String::class.java)
            val cvv = userSnapshot.child("cvv").getValue(String::class.java)
            val cardHolderName = userSnapshot.child("cardHolderName").getValue(String::class.java)

            val hasCardDetails = !cardNumber.isNullOrEmpty() &&
                    !expiryDate.isNullOrEmpty() &&
                    !cvv.isNullOrEmpty() &&
                    !cardHolderName.isNullOrEmpty()

            if (!hasCardDetails) {
                binding.progressBar.visibility = View.GONE
                addMessageToChat(
                    "⚠️ Nu ai detalii de card salvate. Te rugăm să le adaugi în contul tău pentru a plasa comenzi.",
                    Message.Type.BOT
                )
                return@addOnSuccessListener
            }

            val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Nume necunoscut"
            val userPhone = userSnapshot.child("phone").getValue(String::class.java) ?: "Număr necunoscut"
            val userLocation = userSnapshot.child("address").getValue(String::class.java) ?: "Adresă necunoscută"

            val dbRef = FirebaseDatabase.getInstance().getReference("Restaurants")
            dbRef.orderByChild("name").equalTo(restaurantName).get()
                .addOnSuccessListener { restaurantSnapshot ->
                    val restaurant = restaurantSnapshot.children.firstOrNull() ?: run {
                        binding.progressBar.visibility = View.GONE
                        addMessageToChat("Restaurantul nu a fost găsit", Message.Type.BOT)
                        return@addOnSuccessListener
                    }

                    processRestaurantData(
                        restaurant = restaurant,
                        userUid = userUid,
                        userName = userName,
                        userPhone = userPhone,
                        userLocation = userLocation,
                        foodName = menuItem.foodName,
                        foodPrice = menuItem.foodPrice.replace(",", ".").toDoubleOrNull() ?: 0.0,
                        selectie = "${menuItem.foodName} - ${menuItem.foodPrice} RON de la restaurantul: $restaurantName"
                    )
                }
        }
    }

    data class MenuItem(
        val foodName: String,
        val foodPrice: String,
        val foodImage: String,
        val foodDescription: String,
        val foodIngredients: String
    )

    private fun handleOrderSelection(choice: Int) {
        val selectie = sugestiiCurente!![choice - 1]
        sugestiiCurente = null

        val regex = Regex(
            """(.+?) - (\d+(?:\.\d{1,2})?) RON de la restaurantul: (.+?)\. Timp estimativ de livrare: (\d+) mins?""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(selectie) ?: run {
            binding.progressBar.visibility = View.GONE
            addMessageToChat("Eroare la procesarea selecției", Message.Type.BOT)
            return
        }

        val foodName = match.groups[1]?.value?.trim() ?: ""
        val foodPrice = match.groups[2]?.value?.toDoubleOrNull() ?: 0.0
        val restaurantName = match.groups[3]?.value?.trim() ?: ""
        match.groups[4]?.value?.trim() ?: ""
        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val userUid = sharedPref.getString("userId", null)

        val userRef = FirebaseDatabase.getInstance().getReference("user").child(userUid!!)
        userRef.get().addOnSuccessListener { userSnapshot ->

            val cardNumber = userSnapshot.child("cardNumber").getValue(String::class.java)
            val expiryDate = userSnapshot.child("expiryDate").getValue(String::class.java)
            val cvv = userSnapshot.child("cvv").getValue(String::class.java)
            val cardHolderName = userSnapshot.child("cardHolderName").getValue(String::class.java)

            val hasCardDetails = !cardNumber.isNullOrEmpty() &&
                    !expiryDate.isNullOrEmpty() &&
                    !cvv.isNullOrEmpty() &&
                    !cardHolderName.isNullOrEmpty()

            if (!hasCardDetails) {
                binding.progressBar.visibility = View.GONE
                addMessageToChat(
                    "⚠️ Nu ai detalii de card salvate. Te rugăm să le adaugi în contul tău pentru a plasa comenzi.",
                    Message.Type.BOT
                )
                return@addOnSuccessListener
            }

            val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Nume necunoscut"
            val userPhone = userSnapshot.child("phone").getValue(String::class.java) ?: "Număr necunoscut"
            val userLocation = userSnapshot.child("address").getValue(String::class.java) ?: "Adresă necunoscută"

            val dbRef = FirebaseDatabase.getInstance().getReference("Restaurants")
            dbRef.orderByChild("name").equalTo(restaurantName).get()
                .addOnSuccessListener { restaurantSnapshot ->
                    val restaurant = restaurantSnapshot.children.firstOrNull() ?: run {
                        binding.progressBar.visibility = View.GONE
                        addMessageToChat("Restaurantul nu a fost găsit", Message.Type.BOT)
                        return@addOnSuccessListener
                    }

                    processRestaurantData(
                        restaurant = restaurant,
                        userUid = userUid,
                        userName = userName,
                        userPhone = userPhone,
                        userLocation = userLocation,
                        foodName = foodName,
                        foodPrice = foodPrice,
                        selectie = selectie
                    )
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun processRestaurantData(
        restaurant: DataSnapshot,
        userUid: String,
        userName: String,
        userPhone: String,
        userLocation: String,
        foodName: String,
        foodPrice: Double,
        selectie: String
    ) {
        val restaurantLocation =
            restaurant.child("address").getValue(String::class.java) ?: "Locație necunoscută"
        val adminUserId =
            restaurant.child("adminUserId").getValue(String::class.java) ?: "ID Admin necunoscut"
        val restaurantName =
            restaurant.child("name").getValue(String::class.java) ?: "Nume necunoscut"
        val orderTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Geocoder(this@ChatbotActivity, Locale("ro", "RO"))
                } else {
                    Geocoder(this@ChatbotActivity)
                }

                val userAddressList = geocoder.getFromLocationName(userLocation, 1)
                val userLat = userAddressList?.firstOrNull()?.latitude ?: 0.0
                val userLng = userAddressList?.firstOrNull()?.longitude ?: 0.0

                val database = FirebaseDatabase.getInstance()
                val courierSnapshot = database.getReference("couriers")
                    .orderByChild("status").equalTo("AVAILABLE")
                    .limitToFirst(1)
                    .get()
                    .await()

                if (!courierSnapshot.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        addMessageToChat("Niciun curier disponibil în acest moment.", Message.Type.BOT)
                    }
                    return@launch
                }

                val courier = courierSnapshot.children.first()
                val courierId = courier.key ?: return@launch
                val courierLat = courier.child("latitude").getValue(Double::class.java) ?: 0.0
                val courierLng = courier.child("longitude").getValue(Double::class.java) ?: 0.0

                val courierAddress = suspendCancellableCoroutine { continuation ->
                    DistanceCalculationUtility().getAddressFromCoordinates(courierLat, courierLng) { address ->
                        continuation.resume(address) {}
                    }
                }

                if (courierAddress == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        addMessageToChat("Eroare la determinarea adresei curierului.", Message.Type.BOT)
                    }
                    return@launch
                }

                val destination = "$restaurantLocation|$userLocation"
                val estimatedTime = suspendCancellableCoroutine { continuation ->
                    distanceCalculationUtility.getEstimatedDeliveryTime(
                        origin = courierAddress,
                        destination = destination
                    ) { time ->
                        continuation.resume(time) {}
                    }
                }

                if (estimatedTime == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        addMessageToChat("Eroare la estimarea timpului de livrare.", Message.Type.BOT)
                    }
                    return@launch
                }

                val order = OrderDetails(
                    userId = userUid,
                    name = userName,
                    foodItemName = arrayListOf(foodName),
                    foodItemPrice = arrayListOf(foodPrice.toString()),
                    foodItemImage = arrayListOf(""),
                    foodItemQuantities = arrayListOf(1),
                    foodItemDescriptions = arrayListOf(""),
                    foodItemIngredients = arrayListOf(""),
                    userLocation = userLocation,
                    userLocationLat = userLat.toString(),
                    userLocationLng = userLng.toString(),
                    restaurantLocation = restaurantLocation,
                    totalAmount = foodPrice.toString(),
                    phone = userPhone,
                    orderTime = orderTime,
                    itemPushKey = null,
                    orderAccepted = true,
                    paymentReceived = false,
                    adminUserId = adminUserId,
                    restaurantName = restaurantName,
                    courierId = courierId,
                    estimatedDeliveryTime = estimatedTime
                )

                val orderRef = database.getReference("orders").push()
                val orderId = orderRef.key ?: throw Exception("Failed to generate order ID")
                orderRef.setValue(order).await()

                val courierRef = database.getReference("couriers").child(courierId)
                val courierUpdates = mapOf(
                    "status" to "DELIVERING",
                    "orderId" to orderId,
                    "restaurantLatitude" to restaurant.child("latitude").getValue(Double::class.java),
                    "restaurantLongitude" to restaurant.child("longitude").getValue(Double::class.java),
                    "userLatitude" to userLat,
                    "userLongitude" to userLng
                )
                courierRef.updateChildren(courierUpdates).await()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatbotActivity, "CourierId: $courierId", Toast.LENGTH_SHORT).show()
                    addMessageToChat(
                        "Ai ales:\n$selectie\nComanda ta a fost plasată cu succes!",
                        Message.Type.BOT
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    addMessageToChat("Eroare la procesarea comenzii.", Message.Type.BOT)
                }
            }
        }
    }

    // TODO: check why it's not used
    private suspend fun saveOrderToFirebase(order: OrderDetails) {
        try {
            FirebaseDatabase.getInstance().getReference("orders").push().setValue(order).await()
            withContext(Dispatchers.Main) {
                addMessageToChat("✅ Comanda a fost salvată și trimisă cu succes!", Message.Type.BOT)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                addMessageToChat("❌ A apărut o eroare la trimiterea comenzii. Te rugăm să încerci din nou.", Message.Type.BOT)
            }
        }
    }

    private fun handleFoodSearch(userMessage: String) {
        val directRegex = Regex("(.+?) cu valoarea de maxim (\\d+) ron", RegexOption.IGNORE_CASE)
        val directMatch = directRegex.find(userMessage)

        val produs = directMatch?.groups?.get(1)?.value?.trim()
        val pretMaxim = directMatch?.groups?.get(2)?.value?.toDoubleOrNull()

        if (!produs.isNullOrEmpty() && pretMaxim != null) {
            searchByFoodAndPrice(produs, pretMaxim)
        } else {
            GeminiApiHelper.getGeminiResponse(userMessage) { botReply ->
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    addMessageToChat(botReply, Message.Type.BOT)
                }

                val geminiMatch = directRegex.find(botReply)
                val extractedProdus = geminiMatch?.groups?.get(1)?.value?.trim()
                val extractedPret = geminiMatch?.groups?.get(2)?.value?.toDoubleOrNull()

                if (!extractedProdus.isNullOrEmpty() && extractedPret != null) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    searchByFoodAndPrice(extractedProdus, extractedPret)
                }
            }
        }
    }

    // TODO: Uncomment when actually testing
    private fun getAddressLatLng(address: String, callback: (Double, Double) -> Unit) {
        // Mock coordinates for testing
//        callback(44.4268, 26.1025) // Example coordinates for Bucharest
        
        val apiKey = "AIzaSyBgUEkRTXHL_1Z7zK8bPFoy9QswouPd27A" // Your Google API Key

        // Create the URL for the Geocoding API
        val encodedAddress = URLEncoder.encode(address, "UTF-8")
        val url =
            "https://maps.googleapis.com/maps/api/geocode/json?address=$encodedAddress&key=$apiKey"

        // Make the HTTP request
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failure here
                callback(0.0, 0.0) // Return a default value if the request fails
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body?.string()
                    val jsonObject = JSONObject(jsonResponse)
                    val results = jsonObject.getJSONArray("results")

                    if (results.length() > 0) {
                        val firstResult = results.getJSONObject(0)
                        val geometry = firstResult.getJSONObject("geometry")
                        val location = geometry.getJSONObject("location")
                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")

                        // Return the latitude and longitude through the callback
                        callback(lat, lng)
                    } else {
                        callback(0.0, 0.0) // Return default values if no results found
                    }
                } else {
                    callback(0.0, 0.0) // Return default values if the response is unsuccessful
                }
            }
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun searchByFoodAndPrice(foodName: String, maxPrice: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val dbRef = FirebaseDatabase.getInstance().getReference("user")

            dbRef.child(userId).get().addOnSuccessListener { snapshot ->
                val address = snapshot.child("address").getValue(String::class.java) ?: ""

                if (address.isNotEmpty()) {
                    getAddressLatLng(address) { userLat, userLng ->
                        val restaurantRef =
                            FirebaseDatabase.getInstance().getReference("Restaurants")

                        restaurantRef.get().addOnSuccessListener { restaurantSnapshot ->
                            val suggestions = mutableListOf<String>()
                            val restaurantMenus = mutableMapOf<String, List<MenuItem>>()
                            val deferredList = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

                            lifecycleScope.launch(Dispatchers.IO) {
                                for (restaurant in restaurantSnapshot.children) {
                                    val restaurantName =
                                        restaurant.child("name").getValue(String::class.java)
                                            ?: "Restaurant necunoscut"
                                    val menuItems = restaurant.child("menu").children
                                    val restaurantLat =
                                        restaurant.child("latitude").getValue(Double::class.java)
                                            ?: 0.0
                                    val restaurantLng =
                                        restaurant.child("longitude").getValue(Double::class.java)
                                            ?: 0.0

                                    val deferred = async {
                                        suspendCancellableCoroutine { cont ->
                                            DeliveryUtils.calculateDeliveryTime(
                                                userLat,
                                                userLng,
                                                restaurantLat,
                                                restaurantLng,
                                            ) { deliveryTime ->
                                                val menuItemsList = mutableListOf<MenuItem>()
                                                var hasMatchingItems = false

                                                for (item in menuItems) {
                                                    menuItemsList.add(
                                                        MenuItem(
                                                            foodName = item.child("foodName").getValue(String::class.java) ?: "",
                                                            foodPrice = item.child("foodPrice").getValue(String::class.java) ?: "",
                                                            foodImage = item.child("foodImage").getValue(String::class.java) ?: "",
                                                            foodDescription = item.child("foodDescription").getValue(String::class.java) ?: "",
                                                            foodIngredients = item.child("foodIngredients").getValue(String::class.java) ?: ""
                                                        )
                                                    )

                                                    val numeProdus = item.child("foodName")
                                                        .getValue(String::class.java)?.lowercase()
                                                        ?: ""
                                                    val rawPrice = item.child("foodPrice")
                                                        .getValue(String::class.java)
                                                        ?.lowercase()
                                                        ?.replace("[^0-9,.]".toRegex(), "") ?: ""
                                                    val pretProdus =
                                                        rawPrice.replace(",", ".").toDoubleOrNull()
                                                            ?: Double.MAX_VALUE

                                                    if (numeProdus.contains(foodName.lowercase()) && pretProdus <= maxPrice && suggestions.size < 3) {
                                                        hasMatchingItems = true
                                                        suggestions.add("$numeProdus - $pretProdus RON de la restaurantul: $restaurantName. Timp estimativ de livrare: $deliveryTime")
                                                    }
                                                }

                                                if (hasMatchingItems) {
                                                    restaurantMenus[restaurantName] = menuItemsList
                                                }
                                                cont.resume(Unit) {}
                                            }
                                        }
                                    }
                                    deferredList.add(deferred)
                                }

                                deferredList.forEach { it.await() }

                                val response = if (suggestions.isNotEmpty()) {
                                    val formattedList =
                                        suggestions.mapIndexed { index, s -> "${index + 1}. $s" }
                                            .joinToString("\n")
                                    "🍽 Iată câteva sugestii pentru **\"$foodName\"** sub **$maxPrice RON**:\n\n$formattedList\n\n✋ Răspunde cu *1*, *2* sau *3* pentru a comanda.\n\n📋 Pentru a vedea meniul complet al unui restaurant, răspunde cu *meniu* urmat de numărul restaurantului (ex: meniu 1)."
                                } else {
                                    "😞 Din păcate, nu am găsit produse **\"$foodName\"** sub **$maxPrice RON** în acest moment."
                                }

                                withContext(Dispatchers.Main) {
                                    binding.progressBar.visibility = View.GONE
                                    addMessageToChat(response, Message.Type.BOT)
                                    sugestiiCurente = suggestions
                                    currentRestaurantMenus = restaurantMenus
                                }
                            }
                        }
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    addMessageToChat("Nu am găsit adresa utilizatorului.", Message.Type.BOT)
                }
            }.addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                addMessageToChat(
                    "A apărut o eroare la obținerea datelor din tabelul utilizator.",
                    Message.Type.BOT
                )
            }
        } else {
            binding.progressBar.visibility = View.GONE
            addMessageToChat("Nu ești autentificat. Te rugăm să te autentifici.", Message.Type.BOT)
        }
    }
}