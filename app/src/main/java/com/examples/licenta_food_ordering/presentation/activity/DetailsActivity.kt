package com.examples.licenta_food_ordering.presentation.activity

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.ActivityDetailsBinding
import com.examples.licenta_food_ordering.model.cart.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase


class DetailsActivity : AppCompatActivity() {
    private lateinit var binding : ActivityDetailsBinding
    private var foodName: String?=null
    private var foodImage: String?=null
    private var foodDescriptions: String?=null
    private var foodPrice: String?=null
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding=ActivityDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth=FirebaseAuth.getInstance()
        foodName=intent.getStringExtra("MenuItemName")
        foodDescriptions=intent.getStringExtra("MenuItemDescription")
        foodPrice=intent.getStringExtra("MenuItemPrice")
        foodImage=intent.getStringExtra("MenuItemImage")

        with(binding){
            detailFoodName.text=foodName
            detailDescription.text=foodDescriptions
            Glide.with(this@DetailsActivity).load(Uri.parse(foodImage)).into(detailFoodImage)
        }

        binding.imageButton.setOnClickListener {
            finish()
        }

        binding.AddItemButton.setOnClickListener{
            addItemToCart()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun addItemToCart() {
        val database=FirebaseDatabase.getInstance().reference
        val userId=auth.currentUser?.uid?:""
        val cartItem= CartItem(foodName.toString(), foodPrice.toString(), foodDescriptions.toString(), foodImage.toString(), 1)
        database.child("user").child(userId).child("CartItems").push().setValue(cartItem).addOnSuccessListener {
            Toast.makeText(this, "Items added into cart successfully", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
               Toast.makeText(this, "Item NOT added", Toast.LENGTH_SHORT).show()
        }
    }
}