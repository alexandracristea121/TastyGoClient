package com.examples.licenta_food_ordering.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.FragmentHomeBinding
import com.examples.licenta_food_ordering.adapter.PopularItemsAdapter
import com.examples.licenta_food_ordering.adapter.SuggestedFoodAdapter
import com.examples.licenta_food_ordering.model.cart.CartItem
import com.examples.licenta_food_ordering.model.food.MenuItem
import com.examples.licenta_food_ordering.model.food.SuggestedFood
import com.examples.licenta_food_ordering.model.notification.NotificationModel
import com.examples.licenta_food_ordering.presentation.activity.ChatbotActivity
import com.examples.licenta_food_ordering.presentation.activity.Restaurant
import com.examples.licenta_food_ordering.utils.delivery.DeliveryUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var restaurantsList: MutableList<Restaurant> = mutableListOf()
    private lateinit var suggestedFoodAdapter: SuggestedFoodAdapter
    private var suggestedFoodList = mutableListOf<SuggestedFood>()
    private var restaurantCache: MutableMap<String, Restaurant> = mutableMapOf()
    private var lastCacheUpdate: Long = 0
    private val CACHE_DURATION = 5 * 60 * 1000
    private var isCacheValid = false
    private var isLoading = false
    private var lastLoadedKey: String? = null
    private val PAGE_SIZE = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        loadCacheFromPreferences()
    }

    private fun loadCacheFromPreferences() {
        val prefs = requireContext().getSharedPreferences("RestaurantCache", Context.MODE_PRIVATE)
        lastCacheUpdate = prefs.getLong("lastUpdate", 0)
        
        isCacheValid = System.currentTimeMillis() - lastCacheUpdate < CACHE_DURATION
        
        if (isCacheValid) {
            val cachedData = prefs.getString("restaurants", null)
            if (cachedData != null) {
                try {
                    val type = object : TypeToken<Map<String, Restaurant>>() {}.type
                    restaurantCache = Gson().fromJson(cachedData, type)
                    restaurantsList = restaurantCache.values.toMutableList()
                    displayAllRestaurants()
                } catch (e: Exception) {
                    Log.e("Cache", "Error loading cache: ${e.message}")
                    isCacheValid = false
                }
            }
        }
    }

    private fun saveCacheToPreferences() {
        val prefs = requireContext().getSharedPreferences("RestaurantCache", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        try {
            val json = Gson().toJson(restaurantCache)
            editor.putString("restaurants", json)
            editor.putLong("lastUpdate", System.currentTimeMillis())
            editor.apply()
            isCacheValid = true
        } catch (e: Exception) {
            Log.e("Cache", "Error saving cache: ${e.message}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

// TODO: this are helper functions used in the past
//    fetchLogosForRestaurants("timisoara")
//    getCurrentLocationAndFetchRestaurants()
//    convertAllRestaurantAddressesToLatLong(requireContext())
//    updateAdminUserIds()
//    deleteAllRestaurants()
//    fetchRestaurantNamesFromTazz()
//    fetchLogosForRestaurants()

        retrieveAndDisplayAllRestaurantsFromDatabase()
        return binding.root
    }

    private fun deleteAllRestaurants() {
        val database = FirebaseDatabase.getInstance()
        val restaurantsRef = database.getReference("Restaurants")

        restaurantsRef.removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(
                    requireContext(),
                    "All restaurants deleted successfully.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Error deleting restaurants: ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun getCurrentLocationAndFetchRestaurants() {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude

                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                val city = addresses?.get(0)?.locality

                if (city != null) {
                    normalizeCityName(city)
                    fetchMenusFromTazz(city)
                } else {
                    Toast.makeText(requireContext(), "City not found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Unable to retrieve location", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocationAndFetchRestaurants()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Location permission is required to fetch restaurants.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private fun fetchRestaurantNamesFromTazz(city: String) {
        val url = "https://tazz.ro/timisoara/restaurante"

        Thread {
            try {
                val doc = Jsoup.connect(url).get()
                val restaurantNames = doc.select("h3.store-name")

                for (nameElement in restaurantNames) {
                    val restaurantName = nameElement.text().trim()
                    saveRestaurantNameToFirebase(restaurantName)
                }

                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Fetched restaurant names successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Error fetching from Tazz: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun fetchLogosForRestaurants() {
        val normalizedCity = "timisoara"

        val url = "https://tazz.ro/$normalizedCity/restaurante"

        Thread {
            try {
                val doc = Jsoup.connect(url).get()

                val restaurantCards = doc.select("div.store-card")
                val updates = mutableListOf<Pair<String, String>>()

                for (card in restaurantCards) {
                    val name = card.selectFirst("h3.store-name")?.text()?.trim()
                    val logo = card.selectFirst("img.logo-cover")?.attr("src")?.trim()

                    if (!name.isNullOrEmpty() && !logo.isNullOrEmpty()) {
                        updates.add(name to logo)
                    }
                }

                println("Found ${updates.size} restaurants with logos")

                for ((name, logoUrl) in updates) {
                    updateLogoInFirebase(name, logoUrl)
                }

                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Logos updated for ${updates.size} restaurants.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateLogoInFirebase(restaurantName: String, logoUrl: String) {
        val ref = FirebaseDatabase.getInstance().getReference("Restaurants")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var matchFound = false

                for (child in snapshot.children) {
                    val firebaseName = child.child("name").getValue(String::class.java)?.lowercase()?.trim()
                    val tazzName = restaurantName.lowercase().trim()

                    if (firebaseName == tazzName) {
                        child.ref.child("logo").setValue(logoUrl)
                        println("✅ Logo added for \"$restaurantName\" -> $logoUrl")
                        matchFound = true
                        break
                    }
                }

                if (!matchFound) {
                    println("❌ No match found in Firebase for: \"$restaurantName\"")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ Firebase error: ${error.message}")
            }
        })
    }
    private fun saveRestaurantNameToFirebase(restaurantName: String) {
        val database = FirebaseDatabase.getInstance()
        val restaurantsRef = database.getReference("Restaurants")
        val normalizedName = normalizeName(restaurantName)

        restaurantsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var alreadyExists = false

                for (child in snapshot.children) {
                    val existingName = child.child("name").getValue(String::class.java) ?: continue
                    val normalizedExistingName = normalizeName(existingName)

                    if (normalizedExistingName == normalizedName) {
                        alreadyExists = true
                        break
                    }
                }

                if (!alreadyExists) {
                    val restaurantId = restaurantsRef.push().key
                    val restaurantData = mapOf(
                        "id" to restaurantId,
                        "name" to restaurantName,
                        "latitude" to 0.0,
                        "longitude" to 0.0,
                        "address" to "",
                        "adminUserId" to "adminUserId",
                        "menu" to mapOf<String, MenuItem>(),
                        "categories" to listOf<String>(),
                        "phoneNumber" to ""
                    )

                    restaurantId?.let {
                        restaurantsRef.child(it).setValue(restaurantData)
                        Toast.makeText(
                            requireContext(),
                            "$restaurantName added to the database!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "$restaurantName already exists in the database.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "DB Error: ${error.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun normalizeName(name: String): String {
        return name
            .lowercase()
            .replace("ă", "a")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ș", "s")
            .replace("ț", "t")
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun retrieveAndDisplayAllRestaurantsFromDatabase() {
        if (isCacheValid && restaurantCache.isNotEmpty()) {
            restaurantsList = restaurantCache.values.toMutableList()
            displayAllRestaurants()
            return
        }

        if (isLoading) return
        isLoading = true
        binding.progressBar.visibility = View.VISIBLE

        database = FirebaseDatabase.getInstance()
        val restaurantsRef: DatabaseReference = database.reference.child("Restaurants")
        
        restaurantsRef.keepSynced(true)

        val query = restaurantsRef.orderByKey()
            .limitToFirst(PAGE_SIZE)
            .apply {
                if (lastLoadedKey != null) {
                    startAfter(lastLoadedKey)
                }
            }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (lastLoadedKey == null) {
                    restaurantCache.clear()
                    restaurantsList.clear()
                }
                
                for (restaurantSnapshot in snapshot.children) {
                    Log.d("RestaurantData", "Processing restaurant: ${restaurantSnapshot.key}")
                    val restaurant = restaurantSnapshot.getValue(Restaurant::class.java)
                    restaurant?.let { 
                        restaurantsList.add(it)
                        restaurantCache[restaurantSnapshot.key ?: ""] = it
                    }
                    lastLoadedKey = restaurantSnapshot.key
                }

                if (lastLoadedKey == null) {
                    restaurantsList = restaurantsList.filter { restaurant ->
                        !restaurant.menu.isNullOrEmpty() && restaurant.address.isNotBlank()
                    }.toMutableList()
                }

                if (restaurantsList.isNotEmpty()) {
                    displayAllRestaurants()
                    saveCacheToPreferences()
                } else if (lastLoadedKey == null) {
                    Toast.makeText(
                        requireContext(),
                        "No restaurants with menu and address found.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                isLoading = false
                binding.progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Failed to load restaurants: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun loadMoreRestaurants() {
        if (isLoading || lastLoadedKey == null) return
        retrieveAndDisplayAllRestaurantsFromDatabase()
    }

    private fun displayAllRestaurants() {
        Log.d("HomeFragment", "Total restaurants fetched: ${restaurantsList.size}")
        setRestaurantsAdapter(restaurantsList)
    }

    private fun setRestaurantsAdapter(allRestaurants: List<Restaurant>) {
        if (binding.popularRecyclerView.adapter == null) {
            val adapter = PopularItemsAdapter(allRestaurants, requireContext())
            binding.popularRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            binding.popularRecyclerView.adapter = adapter
            
            binding.popularRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0) {
                        loadMoreRestaurants()
                    }
                }
            })
        } else {
            (binding.popularRecyclerView.adapter as PopularItemsAdapter).updateRestaurants(allRestaurants)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.categoriesRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        suggestedFoodAdapter = SuggestedFoodAdapter(suggestedFoodList, object : SuggestedFoodAdapter.OnAddToCartClickListener {
            override fun onAddToCartClicked(food: SuggestedFood) {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Toast.makeText(requireContext(), "Please login to add items to cart", Toast.LENGTH_SHORT).show()
                    return
                }

                val cartItem = CartItem(
                    foodName = food.foodName,
                    foodPrice = food.foodPrice,
                    foodImage = food.foodImageResId,
                    foodQuantity = 1
                )

                val database = FirebaseDatabase.getInstance()
                val cartRef = database.reference
                    .child("user")
                    .child(currentUser.uid)
                    .child("CartItems")

                cartRef.orderByChild("foodName").equalTo(food.foodName)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                for (itemSnapshot in snapshot.children) {
                                    val currentQuantity = itemSnapshot.child("foodQuantity").getValue(Int::class.java) ?: 0
                                    itemSnapshot.ref.child("foodQuantity").setValue(currentQuantity + 1)
                                }
                                Toast.makeText(requireContext(), "Updated quantity of ${food.foodName} in cart!", Toast.LENGTH_SHORT).show()
                            } else {
                                cartRef.push().setValue(cartItem)
                                    .addOnSuccessListener {
                                        Toast.makeText(requireContext(), "${food.foodName} added to cart!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(requireContext(), "Failed to add item to cart", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        })

        binding.categoriesRecyclerView.adapter = suggestedFoodAdapter
        binding.prevButton.setOnClickListener {
            val layoutManager = binding.categoriesRecyclerView.layoutManager as LinearLayoutManager
            val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
            if (firstVisibleItem > 0) {
                binding.categoriesRecyclerView.smoothScrollToPosition(firstVisibleItem - 1)
            } else {
                binding.categoriesRecyclerView.smoothScrollToPosition(suggestedFoodList.size - 1)
            }
        }

        binding.nextButton.setOnClickListener {
            val layoutManager = binding.categoriesRecyclerView.layoutManager as LinearLayoutManager
            val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
            if (lastVisibleItem < suggestedFoodList.size - 1) {
                binding.categoriesRecyclerView.smoothScrollToPosition(lastVisibleItem + 1)
            } else {
                binding.categoriesRecyclerView.smoothScrollToPosition(0)
            }
        }

        setupSearch()
        fetchMenuItems()

        val bellIcon = requireActivity().findViewById<ImageView>(R.id.imageView5)
        val notificationDot = requireActivity().findViewById<View>(R.id.notificationDot)
        val pendingNotifications = mutableListOf<NotificationModel>()

        checkForPendingOrders { notifications ->
            if (notifications.isNotEmpty()) {
                notificationDot.visibility = View.VISIBLE
                pendingNotifications.clear()
                pendingNotifications.addAll(notifications)
            } else {
                notificationDot.visibility = View.GONE
            }
        }

        bellIcon.setOnClickListener {
            if (pendingNotifications.isEmpty()) {
                Toast.makeText(requireContext(), "No new notifications", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val popup = PopupMenu(requireContext(), bellIcon)
            pendingNotifications.forEachIndexed { index, notification ->
                popup.menu.add(0, index, 0, notification.message)
            }

            popup.setOnMenuItemClickListener { item ->
                val selectedNotification = pendingNotifications[item.itemId]

                val bundle = Bundle().apply {
                    putString("orderId", selectedNotification.orderId)
                    putString("restaurantName", selectedNotification.restaurantName)
                    putStringArrayList("foodNames", ArrayList(selectedNotification.foodNames))
                    putString("estimatedDeliveryTime", selectedNotification.estimatedDeliveryTime)
                }

                findNavController().navigate(R.id.notificationsFragment, bundle)
                true
            }

            popup.show()
        }

        binding.chatbotButton.setOnClickListener {
            Toast.makeText(requireContext(), "Launching chatbot...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), ChatbotActivity::class.java))
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val searchQuery = s?.toString()?.trim() ?: ""
                if (searchQuery.isEmpty()) {
                    displayAllRestaurants()
                } else {
                    val filteredRestaurants = restaurantsList.filter { restaurant ->
                        restaurant.name.contains(searchQuery, ignoreCase = true)
                    }
                    setRestaurantsAdapter(filteredRestaurants)
                }
            }
        })
    }

    private fun fetchMenuItems() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val databaseRef = FirebaseDatabase.getInstance().getReference("orders")

        databaseRef.orderByChild("adminUserId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val foodCountMap = mutableMapOf<String, Triple<Int, String, String>>()
                    var restaurantName: String? = null

                    for (orderSnapshot in snapshot.children) {
                        val foodNames = orderSnapshot.child("foodNames").getValue(object : GenericTypeIndicator<List<String>>() {})
                        val foodImages = orderSnapshot.child("foodImages").getValue(object : GenericTypeIndicator<List<String>>() {})
                        val foodPrices = orderSnapshot.child("foodPrices").getValue(object : GenericTypeIndicator<List<String>>() {})
                        val foodQuantities = orderSnapshot.child("foodQuantities").getValue(object : GenericTypeIndicator<List<Int>>() {})
                        val orderRestaurantName = orderSnapshot.child("restaurantName").getValue(String::class.java)

                        if (restaurantName == null && orderRestaurantName != null) {
                            restaurantName = orderRestaurantName
                        }

                        if (foodNames != null && foodImages != null && foodPrices != null && foodQuantities != null) {
                            for (i in foodNames.indices) {
                                val name = foodNames[i]
                                val image = foodImages.getOrNull(i) ?: ""
                                val price = foodPrices.getOrNull(i) ?: ""
                                val quantity = foodQuantities.getOrNull(i) ?: 0

                                val existing = foodCountMap[name]
                                if (existing != null) {
                                    foodCountMap[name] = Triple(existing.first + quantity, existing.second, existing.third)
                                } else {
                                    foodCountMap[name] = Triple(quantity, image, price)
                                }
                            }
                        }
                    }

                    val topOrderedFoods = foodCountMap.entries
                        .sortedByDescending { it.value.first }
                        .take(4)

                    val topFoodItems = topOrderedFoods.map {
                        SuggestedFood(
                            restaurantName = restaurantName ?: "Unknown Restaurant",
                            foodName = it.key,
                            foodPrice = it.value.third + " RON",
                            foodImageResId = it.value.second
                        )
                    }.toMutableList()

                    suggestedFoodList.clear()
                    suggestedFoodList.addAll(topFoodItems)
                    suggestedFoodAdapter.notifyDataSetChanged()

                    binding.prevButton.visibility = if (topFoodItems.size > 1) View.VISIBLE else View.GONE
                    binding.nextButton.visibility = if (topFoodItems.size > 1) View.VISIBLE else View.GONE

                    binding.categoriesRecyclerView.apply {
                        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                        setHasFixedSize(true)
                        addOnScrollListener(object : RecyclerView.OnScrollListener() {
                            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                                super.onScrollStateChanged(recyclerView, newState)
                                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                                    
                                    if (firstVisibleItem != lastVisibleItem) {
                                        val firstView = layoutManager.findViewByPosition(firstVisibleItem)
                                        val lastView = layoutManager.findViewByPosition(lastVisibleItem)
                                        
                                        if (firstView != null && lastView != null) {
                                            val firstViewRight = firstView.right
                                            val lastViewLeft = lastView.left
                                            
                                            if (firstViewRight > lastViewLeft) {
                                                recyclerView.smoothScrollToPosition(firstVisibleItem)
                                            } else {
                                                recyclerView.smoothScrollToPosition(lastVisibleItem)
                                            }
                                        }
                                    }
                                }
                            }
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("HomeFragment", "Database error: ${error.message}")
                }
            })
    }

    private fun normalizeCityName(city: String): String {
        val normalized = java.text.Normalizer.normalize(city, java.text.Normalizer.Form.NFD)
            .replace("[\\p{InCombiningDiacriticalMarks}]", "")
        return normalized.lowercase().replace(" ", "-")
    }

    private fun fetchMenusFromTazz(city: String) {
        val normalizedCity = city
            .lowercase()
            .replace("ă", "a")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ș", "s")
            .replace("ț", "t")

        val url = "https://tazz.ro/$normalizedCity/restaurante"

        val restaurantsRef = FirebaseDatabase.getInstance().reference.child("Restaurants")

        restaurantsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val restaurantsList = mutableListOf<Restaurant>()
                for (restaurantSnapshot in snapshot.children) {
                    Log.d("RestaurantData", "Processing restaurant: ${restaurantSnapshot.key}")
                    val restaurant = restaurantSnapshot.getValue(Restaurant::class.java)
                    if (restaurant != null) {
                        restaurantsList.add(restaurant)
                    }
                }

                Thread {
                    try {
                        val doc = Jsoup.connect(url).get()
                        val restaurantLinks =
                            doc.select("a[href^=https://tazz.ro/][href*=/restaurant]")

                        for (link in restaurantLinks) {
                            val href = link.attr("href")
                            val slug = href.split("/").getOrNull(4) ?: continue

                            val matchedRestaurant = restaurantsList.find { restaurant ->
                                val formattedName = restaurant.name
                                    .lowercase()
                                    .replace("ă", "a")
                                    .replace("â", "a")
                                    .replace("î", "i")
                                    .replace("ș", "s")
                                    .replace("ț", "t")
                                    .replace(Regex("[^a-z0-9 ]"), "")
                                    .replace("\\s+".toRegex(), "-")
                                formattedName == slug
                            }

                            matchedRestaurant?.let { restaurant ->
                                val menuItems = scrapeMenuFromRestaurantPage(href)
                                if (menuItems.isNotEmpty()) {
                                    saveMenuToFirebase(restaurant, menuItems)
                                }
                            }
                        }

                    } catch (e: org.jsoup.HttpStatusException) {
                        Log.e(
                            "TazzScraper",
                            "HTTP error fetching URL. Status code: ${e.statusCode}, URL: ${e.url}"
                        )
                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "HTTP error ${e.statusCode} while fetching from Tazz",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    } catch (e: Exception) {
                        Log.e("TazzScraper", "General error: ${e.message}", e)
                        activity?.runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "Error fetching from Tazz: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }.start()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TazzScraper", "Failed to load restaurants: ${error.message}")
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load restaurant data",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun fetchRestaurantsAddressFromTazz(city: String) {
        val normalizedCity = city
            .lowercase()
            .replace("ă", "a")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ș", "s")
            .replace("ț", "t")

        val url = "https://tazz.ro/$normalizedCity/restaurante"

        Thread {
            try {
                val doc = Jsoup.connect(url).get()
                val restaurantLinks = doc.select("a[href^=https://tazz.ro/][href*=/restaurant]")

                for (link in restaurantLinks) {
                    val href = link.attr("href")
                    val parts = href.split("/")

                    val citySlug = parts.getOrNull(3) ?: continue
                    val restaurantSlug = parts.getOrNull(4) ?: continue
                    val restaurantId = parts.getOrNull(5) ?: continue

                    val infoUrl =
                        "https://tazz.ro/$citySlug/$restaurantSlug/$restaurantId/partener/informatii"

                    try {
                        val infoDoc = Jsoup.connect(infoUrl).get()
                        val addressElement = infoDoc.selectFirst("div.address")
                        val scrapedAddress = addressElement?.text() ?: "Address not found"

                        val matchedRestaurant = restaurantsList.find { restaurant ->
                            val formattedName = restaurant.name
                                .lowercase()
                                .replace("ă", "a")
                                .replace("â", "a")
                                .replace("î", "i")
                                .replace("ș", "s")
                                .replace("ț", "t")
                                .replace(Regex("[^a-z0-9 ]"), "")
                                .replace("\\s+".toRegex(), "-")
                            formattedName == restaurantSlug
                        }

                        matchedRestaurant?.let { restaurant ->
                            val dbRef = FirebaseDatabase.getInstance()
                                .getReference("Restaurants")
                                .child(restaurant.id)
                                .child("address")

                            dbRef.setValue(scrapedAddress)
                                .addOnSuccessListener {
                                    Log.d(
                                        "TazzAddress",
                                        "Updated address for ${restaurant.name}: $scrapedAddress"
                                    )
                                }
                                .addOnFailureListener { e ->
                                    Log.e(
                                        "TazzAddress",
                                        "Failed to update address for ${restaurant.name}: ${e.message}"
                                    )
                                }
                        }

                    } catch (e: Exception) {
                        Log.e(
                            "TazzAddress",
                            "Error scraping info for $restaurantSlug: ${e.message}"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("TazzAddress", "Error loading restaurant list: ${e.message}", e)
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Error fetching addresses: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }


    private fun scrapeMenuFromRestaurantPage(url: String): List<MenuItem> {
        val menuItems = mutableListOf<MenuItem>()

        try {
            val doc = Jsoup.connect(url).get()

            val productCards = doc.select("div.restaurant-product-card")
            for (card in productCards) {
                val name = card.select("h5.title-container").text().trim()
                val description = card.select("p.description-container").text().trim()
                val price = card.select("span.price-container").text().trim()
                val imageUrl = card.select("div.image-container img").attr("src").trim()

                val menuItem = MenuItem(
                    foodName = name,
                    foodPrice = price,
                    foodDescription = description,
                    foodImage = imageUrl
                )
                menuItems.add(menuItem)
            }

            val categoryMap = mutableMapOf<String, String>()
            val sectionTitles = doc.select("div.js-section-title")

            for (section in sectionTitles) {
                val id = section.id()
                val subcategoryId = id.removePrefix("subcategory-")
                val titleElement = section.nextElementSibling()
                if (titleElement != null && titleElement.tagName() == "h2" && titleElement.hasClass(
                        "widget-title"
                    )
                ) {
                    categoryMap[subcategoryId] = titleElement.text().trim()
                }
            }

            val dialogOpeners = doc.select("tz-product-dialog-opener")

            for (element in dialogOpeners) {
                val name = element.selectFirst("h5.title-container")?.text()?.trim() ?: continue
                val description =
                    element.selectFirst("p.description-container")?.text()?.trim() ?: ""
                val price = element.selectFirst("span.price-container")?.text()?.trim()
                    ?: element.selectFirst("span.product-price.promo.zprice")?.text()?.trim()
                    ?: element.selectFirst("span.product-price.zprice")?.text()?.trim()
                    ?: ""
                val imageUrl = element.selectFirst("img.img-product")?.attr("src")?.trim() ?: ""

                val openerId = element.id()
                val subcategoryMatch = Regex("subcategory-(\\d+)-product").find(openerId)
                val subcategoryId = subcategoryMatch?.groupValues?.get(1)
                val category = subcategoryId?.let { categoryMap[it] } ?: "Uncategorized"

                val menuItem = MenuItem(
                    foodName = name,
                    foodPrice = price,
                    foodDescription = description,
                    foodImage = imageUrl,
                    foodCategory = category
                )

                menuItems.add(menuItem)
            }

        } catch (e: Exception) {
            Log.e("scrapeMenu", "Error scraping menu: ${e.message}")
        }

        return menuItems
    }

    private fun scrapeCategoryForMenuItems(city: String) {
        val normalizedCity = city
            .lowercase()
            .replace("ă", "a")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ș", "s")
            .replace("ț", "t")

        val url = "https://tazz.ro/$normalizedCity/restaurante"

        Thread {
            try {
                val doc = Jsoup.connect(url).get()
                val restaurantLinks = doc.select("a[href^=https://tazz.ro/][href*=/restaurant]")

                for (link in restaurantLinks) {
                    val href = link.attr("href")
                    val parts = href.split("/")
                    val restaurantSlug = parts.getOrNull(4) ?: continue
                    val partnerId = parts.getOrNull(5) ?: continue

                    val matchedRestaurant = restaurantsList.find { restaurant ->
                        val formattedName = restaurant.name
                            .lowercase()
                            .replace("ă", "a")
                            .replace("â", "a")
                            .replace("î", "i")
                            .replace("ș", "s")
                            .replace("ț", "t")
                            .replace(Regex("[^a-z0-9 ]"), "")
                            .replace("\\s+".toRegex(), "-")
                        formattedName == restaurantSlug
                    }

                    matchedRestaurant?.let { restaurant ->
                        val restaurantId = restaurant.id
                        val menuRef = FirebaseDatabase.getInstance()
                            .getReference("Restaurants")
                            .child(restaurantId)
                            .child("menu")

                        menuRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (!snapshot.exists()) return

                                val menuUrl =
                                    "https://tazz.ro/$normalizedCity/$restaurantSlug/$partnerId/restaurant"

                                try {
                                    val restaurantDoc = Jsoup.connect(menuUrl).get()

                                    val categoryMap = mutableMapOf<String, String>()
                                    val sections = restaurantDoc.select("section")
                                    for (section in sections) {
                                        val header = section.selectFirst("h2.widget-title")?.text()
                                            ?: continue
                                        val subcategoryId = section.attr("id")
                                        if (subcategoryId.startsWith("subcategory-")) {
                                            categoryMap[subcategoryId.removePrefix("subcategory-")] =
                                                header
                                        }
                                    }

                                    val productElements =
                                        restaurantDoc.select("tz-product-dialog-opener")

                                    for (menuItemSnapshot in snapshot.children) {
                                        val menuItem =
                                            menuItemSnapshot.getValue(MenuItem::class.java)
                                                ?: continue
                                        val itemKey = menuItemSnapshot.key ?: continue

                                        val matchingElement = productElements.find {
                                            val title =
                                                it.selectFirst("h5.title-container")?.text()?.trim()
                                            title.equals(
                                                menuItem.foodName?.trim(),
                                                ignoreCase = true
                                            )
                                        }

                                        if (matchingElement != null) {
                                            val openerId = matchingElement.attr("id")
                                            val subcategoryMatch =
                                                Regex("subcategory-(\\d+)-product").find(openerId)
                                            val subcategoryId =
                                                subcategoryMatch?.groupValues?.get(1)
                                            val category = subcategoryId?.let { categoryMap[it] }
                                                ?: "Uncategorized"

                                            menuRef.child(itemKey).child("category")
                                                .setValue(category)
                                        }
                                    }

                                } catch (e: Exception) {
                                    Log.e(
                                        "CategoryScraper",
                                        "Error parsing $menuUrl: ${e.message}",
                                        e
                                    )
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e(
                                    "Firebase",
                                    "Failed to load menu for ${restaurant.name}: ${error.message}"
                                )
                            }
                        })
                    }
                }

            } catch (e: org.jsoup.HttpStatusException) {
                Log.e(
                    "TazzScraper",
                    "HTTP error fetching URL. Status code: ${e.statusCode}, URL: ${e.url}"
                )
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "HTTP error ${e.statusCode} while scraping categories",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("TazzScraper", "General error: ${e.message}", e)
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Error scraping categories: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun saveMenuToFirebase(restaurant: Restaurant, menuItems: List<MenuItem>) {
        val database = FirebaseDatabase.getInstance()
        val restaurantRef = database.getReference("Restaurants").child(restaurant.id)
        val menuRef = restaurantRef.child("menu")

        menuRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingNames = mutableMapOf<String, DataSnapshot>()
                for (menuSnapshot in snapshot.children) {
                    try {
                        val item = menuSnapshot.getValue(MenuItem::class.java)
                        val nameKey = item?.foodName?.trim()?.lowercase()
                        if (nameKey != null) {
                            existingNames[nameKey] = menuSnapshot
                        }
                    } catch (e: Exception) {
                        Log.w(
                            "saveMenu",
                            "Skipping invalid menu entry '${menuSnapshot.key}': ${e.message}"
                        )
                    }
                }

                for (item in menuItems) {
                    val foodNameKey = item.foodName?.trim()?.lowercase() ?: continue

                    if (existingNames.containsKey(foodNameKey)) {
                        val existingSnapshot = existingNames[foodNameKey]
                        val existingItem = existingSnapshot?.getValue(MenuItem::class.java)

                        if (existingItem != null && (existingItem.foodCategory.isNullOrBlank() && !item.foodCategory.isNullOrBlank())) {
                            existingSnapshot.ref.child("foodCategory").setValue(item.foodCategory)
                            Log.d(
                                "saveMenu",
                                "Updated foodCategory for existing item: $foodNameKey"
                            )
                        } else {
                            Log.d("saveMenu", "Skipping duplicate menu item: $foodNameKey")
                        }
                        continue
                    }

                    val menuItemId = menuRef.push().key ?: continue
                    val menuItem = item.copy(
                        key = menuItemId,
                        restaurantName = restaurant.name
                    )

                    menuRef.child(menuItemId).setValue(menuItem)
                        .addOnSuccessListener {
                            Log.d(
                                "saveMenu",
                                "Menu item '${menuItem.foodName}' saved for ${restaurant.name}"
                            )
                        }
                        .addOnFailureListener {
                            Log.e(
                                "saveMenu",
                                "Failed to save menu item '${menuItem.foodName}': ${it.message}"
                            )
                        }

                    existingNames[foodNameKey] = snapshot
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("saveMenu", "Database error: ${error.message}")
            }
        })
    }

    private fun convertAllRestaurantAddressesToLatLong(context: Context) {
        val database = FirebaseDatabase.getInstance()
        val restaurantsRef = database.getReference("Restaurants")

        restaurantsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (restaurantSnapshot in snapshot.children) {
                    val restaurantId = restaurantSnapshot.key ?: continue
                    val address = restaurantSnapshot.child("address").getValue(String::class.java)
                        ?: continue

                    val currentLat =
                        restaurantSnapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val currentLng =
                        restaurantSnapshot.child("longitude").getValue(Double::class.java) ?: 0.0

                    if (currentLat != 0.0 && currentLng != 0.0) {
                        continue
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val geocoder = Geocoder(context)
                            val addresses = geocoder.getFromLocationName(address, 1)

                            if (!addresses.isNullOrEmpty()) {
                                val newLat = addresses[0].latitude
                                val newLng = addresses[0].longitude

                                withContext(Dispatchers.Main) {
                                    restaurantSnapshot.ref.updateChildren(
                                        mapOf(
                                            "latitude" to newLat,
                                            "longitude" to newLng
                                        )
                                    ).addOnSuccessListener {
                                        Log.d(
                                            "GeoUpdate",
                                            "Updated $restaurantId: ($newLat, $newLng)"
                                        )
                                    }.addOnFailureListener { e ->
                                        Log.e(
                                            "GeoUpdate",
                                            "Update failed for $restaurantId: ${e.message}"
                                        )
                                    }
                                }
                            } else {
                                Log.w("GeoUpdate", "No coordinates found for: $address")
                            }
                        } catch (e: Exception) {
                            Log.e("GeoUpdate", "Geocoding error for $address: ${e.message}")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GeoUpdate", "Database error: ${error.message}")
            }
        })
    }

    private fun checkForPendingOrders(callback: (List<NotificationModel>) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val databaseRef = FirebaseDatabase.getInstance().getReference("orders")

        databaseRef.orderByChild("adminUserId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notifications = mutableListOf<NotificationModel>()

                    for (orderSnapshot in snapshot.children) {
                        val orderAccepted = orderSnapshot.child("orderAccepted").getValue(Boolean::class.java) ?: false
                        val orderDelivered = orderSnapshot.child("orderDelivered").getValue(Boolean::class.java) ?: false

                        if (orderAccepted && !orderDelivered) {
                            val orderId = orderSnapshot.key ?: continue
                            val orderTimeStr = orderSnapshot.child("orderTime").getValue(String::class.java)
                            val restaurantName = orderSnapshot.child("restaurantName").getValue(String::class.java) ?: "Unknown"
                            val foodNames = orderSnapshot.child("foodNames").children.mapNotNull { it.getValue(String::class.java) }
                            val userLocation = orderSnapshot.child("userLocation").getValue(String::class.java)
                            val restaurantLocation = orderSnapshot.child("restaurantLocation").getValue(String::class.java)
                            val formattedTime = if (orderTimeStr != null) {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                val date = inputFormat.parse(orderTimeStr)
                                val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                outputFormat.format(date!!)
                            } else {
                                val fallbackFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                fallbackFormat.format(Date())
                            }

                            if (!userLocation.isNullOrEmpty()) {
                                DeliveryUtils.getCoordinatesFromAddress(userLocation) { userLat, userLng ->
                                    if (userLat != 0.0 && userLng != 0.0) {
                                        println("User Latitude: $userLat, User Longitude: $userLng")

                                        if (!restaurantLocation.isNullOrEmpty()) {
                                            DeliveryUtils.getCoordinatesFromAddress(restaurantLocation) { restaurantLat, restaurantLng ->
                                                if (restaurantLat != 0.0 && restaurantLng != 0.0) {
                                                    println("Restaurant Latitude: $restaurantLat, Restaurant Longitude: $restaurantLng")

                                                    DeliveryUtils.calculateDeliveryTime(userLat, userLng, restaurantLat, restaurantLng) { estimatedDeliveryTime ->

                                                        Handler(Looper.getMainLooper()).post {
                                                            val message = "Restaurant: $restaurantName\nMancarea: $foodNames\nOra: $formattedTime\nLivrare Estimata: $estimatedDeliveryTime"
                                                            val notification = NotificationModel(orderId, message, restaurantName, foodNames, estimatedDeliveryTime)
                                                            notifications.add(notification)
                                                            callback(notifications)
                                                        }
                                                    }
                                                } else {
                                                    println("Failed to get valid restaurant coordinates")
                                                }
                                            }
                                        } else {
                                            Handler(Looper.getMainLooper()).post {
                                                val message = "Restaurant: $restaurantName\nFood: $foodNames\nTime: $formattedTime"
                                                val notification = NotificationModel(orderId, message, restaurantName, foodNames)
                                                notifications.add(notification)

                                                if (notifications.size.toLong() == snapshot.childrenCount) {
                                                    callback(notifications)
                                                }
                                            }
                                        }
                                    } else {
                                        println("Failed to get valid user coordinates")
                                    }
                                }
                            } else {
                                Handler(Looper.getMainLooper()).post {
                                    val message = "Restaurant: $restaurantName\nFood: $foodNames\nTime: $formattedTime"
                                    val notification = NotificationModel(orderId, message, restaurantName, foodNames)
                                    notifications.add(notification)

                                    if (notifications.size.toLong() == snapshot.childrenCount) {
                                        callback(notifications)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(emptyList())
                }
            })
    }

    override fun onResume() {
        super.onResume()
        if (!isCacheValid) {
            retrieveAndDisplayAllRestaurantsFromDatabase()
        }
    }
}