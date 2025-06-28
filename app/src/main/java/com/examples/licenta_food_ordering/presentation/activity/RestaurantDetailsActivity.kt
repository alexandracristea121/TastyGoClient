package com.examples.licenta_food_ordering.presentation.activity

import android.annotation.SuppressLint
import com.examples.licenta_food_ordering.utils.preferences.SharedPrefsHelper.saveRestaurantNames
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.ActivityRestaurantDetailsBinding
import com.examples.licenta_food_ordering.adapter.MenuItemsAdapter
import com.examples.licenta_food_ordering.model.food.MenuItem
import com.examples.licenta_food_ordering.utils.preferences.SharedPrefsHelper
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

@Suppress("DEPRECATION")
class RestaurantDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestaurantDetailsBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var restaurantsRef: DatabaseReference
    private lateinit var restaurantId: String
    private lateinit var nameTextView: TextView
    private lateinit var addressTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var menuRecyclerView: RecyclerView
    private lateinit var categorySpinner: Spinner
    private lateinit var restaurantImageView: ImageView

    private lateinit var menuItemsAdapter: MenuItemsAdapter
    private var menuItems = mutableListOf<MenuItem>()
    private var categories = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestaurantDetailsBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance()
        restaurantsRef = database.getReference("Restaurants")
        restaurantId = intent.getStringExtra("RESTAURANT_ID") ?: ""

        if (restaurantId.isEmpty()) {
            Toast.makeText(this, "Restaurant ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        nameTextView = findViewById(R.id.restaurantName)
        addressTextView = findViewById(R.id.restaurantAddress)
        backButton = findViewById(R.id.backButton)
        menuRecyclerView = findViewById(R.id.menuRecyclerView)
        categorySpinner = findViewById(R.id.categoryFilterSpinner)
        restaurantImageView = findViewById(R.id.restaurantImage)

        setupRecyclerView()

        backButton.setOnClickListener {
            onBackPressed()
        }

        fetchRestaurantDetails()
    }

    private fun setupRecyclerView() {
        menuItemsAdapter = MenuItemsAdapter(
            menuItems = menuItems,
            context = this,
            onItemClick = { menuItem ->
                val intent = Intent(this, DetailsActivity::class.java).apply {
                    putExtra("MenuItemName", menuItem.foodName)
                    putExtra("MenuItemImage", menuItem.foodImage)
                    putExtra("MenuItemDescription", menuItem.foodDescription)
                    putExtra("MenuItemPrice", menuItem.foodPrice)
                }
                startActivity(intent)
            }
        )
        binding.menuRecyclerView.apply {
            adapter = menuItemsAdapter
            layoutManager = LinearLayoutManager(this@RestaurantDetailsActivity)
        }
    }

    private fun fetchRestaurantDetails() {
        restaurantsRef.child(restaurantId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val restaurant = snapshot.getValue(Restaurant::class.java)
                if (restaurant != null) {
                    nameTextView.text = restaurant.name
                    if (restaurant.name.isNotEmpty()) {
                        saveRestaurantNames(this@RestaurantDetailsActivity, restaurant.name)
                    }
                    addressTextView.text = "Adresa: " + restaurant.address

                    restaurant.logo?.let { logoUrl ->
                        Glide.with(this@RestaurantDetailsActivity)
                            .load(logoUrl)
                            .centerCrop()
                            .placeholder(R.drawable.food_placeholder)
                            .error(R.drawable.food_placeholder)
                            .into(restaurantImageView)
                    }

                    SharedPrefsHelper.saveAdminUserId(this@RestaurantDetailsActivity, restaurant.adminUserId)

                    fetchCategories()

                    val menu = restaurant.menu
                    if (menu != null) {
                        if (menu.isNotEmpty()) {
                            menuItems.clear()
                            for (menuItem in menu.values) {
                                menuItems.add(menuItem)
                            }
                            menuItemsAdapter.submitList(menuItems)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Restaurant not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error fetching restaurant details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCategories() {
        val menuRef = restaurantsRef.child(restaurantId).child("menu")

        menuRef.get().addOnSuccessListener { snapshot ->
            val uniqueCategories = mutableSetOf<String>()
            for (childSnapshot in snapshot.children) {
                val category = childSnapshot.child("foodCategory").getValue(String::class.java)
                category?.let {
                    uniqueCategories.add(it)
                }
            }

            categories = uniqueCategories.toMutableList()
            categories.add(0, "Categorii")

            setupCategoryFilter()
        }.addOnFailureListener {
            Toast.makeText(this, "Error fetching categories", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCategoryFilter() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedCategory = categories[position]
                if (selectedCategory == "Categorii") {
                    fetchMenuItems()
                } else {
                    fetchMenuItemsByCategory(selectedCategory)
                }
            }

            override fun onNothingSelected(parentView: AdapterView<*>) {}
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchMenuItems() {
        val menuRef: DatabaseReference =
            database.getReference("Restaurants").child(restaurantId).child("menu")
        menuRef.get().addOnSuccessListener { snapshot ->
            menuItems.clear()
            for (childSnapshot in snapshot.children) {
                val menuItem = childSnapshot.getValue(MenuItem::class.java)
                menuItem?.let {
                    menuItems.add(it)
                }
            }
            menuItemsAdapter.notifyDataSetChanged()
        }.addOnFailureListener {
            Toast.makeText(this, "Error fetching menu items", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchMenuItemsByCategory(category: String) {
        val menuRef: DatabaseReference =
            database.getReference("Restaurants").child(restaurantId).child("menu")
        menuRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                menuItems.clear()
                for (childSnapshot in snapshot.children) {
                    val menuItem = childSnapshot.getValue(MenuItem::class.java)
                    menuItem?.let {
                        if (it.foodCategory == category) {
                            menuItems.add(it)
                        }
                    }
                }
                menuItemsAdapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this, "No items found for category: $category", Toast.LENGTH_SHORT)
                    .show()
            }
        }.addOnFailureListener { _ ->
            Toast.makeText(this, "Error fetching filtered menu items", Toast.LENGTH_SHORT).show()
        }
    }
}