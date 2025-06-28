package com.examples.licenta_food_ordering.presentation.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.licenta_food_ordering.R
import com.examples.licenta_food_ordering.presentation.activity.RecentOrdersActivity
import com.example.licenta_food_ordering.databinding.FragmentHistoryBinding
import com.examples.licenta_food_ordering.presentation.activity.OrderDetailsActivity
import com.examples.licenta_food_ordering.model.order.OrderDetails
import com.examples.licenta_food_ordering.adapter.BuyItemsAgainAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OrderHistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private lateinit var buyItemsAgainAdapter: BuyItemsAgainAdapter
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var userId: String
    private var listOfOrderItem: ArrayList<OrderDetails> = arrayListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHistoryBinding.inflate(layoutInflater, container, false)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        userId = auth.currentUser?.uid ?: ""

        retrieveAllOrders()

        binding.recentbuyitem.setOnClickListener {
            seeItemsRecentBuy()
        }

        binding.receivedButton.setOnClickListener {
            updateOrderStatus()
        }

        return binding.root
    }

    private fun retrieveAllOrders() {
        binding.recentbuyitem.visibility = View.INVISIBLE
        val ordersReference: DatabaseReference = database.reference.child("orders")

        ordersReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listOfOrderItem.clear()

                for (orderSnapshot in snapshot.children) {
                    val orderItem = orderSnapshot.getValue(OrderDetails::class.java)
                    orderItem?.let {
                        if (it.userUid == userId) {
                            it.itemPushkey = orderSnapshot.key
                            listOfOrderItem.add(it)
                        }
                    }
                }

                listOfOrderItem.sortByDescending { it.orderTime }

                if (listOfOrderItem.isNotEmpty()) {
                    binding.recentbuyitem.visibility = View.VISIBLE
                    setDataInRecentBuyItem()
                } else {
                    binding.recentbuyitem.visibility = View.GONE
                }

                setPreviousBuyItemsRecyclerView()
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    private fun updateOrderStatus() {
        val itemPushKey = listOfOrderItem.getOrNull(0)?.itemPushkey
        itemPushKey?.let {
            val completeOrderReference = database.reference.child("CompletedOrder").child(it)
            completeOrderReference.child("paymentReceived").setValue(true)
        }
    }

    private fun seeItemsRecentBuy() {
        listOfOrderItem.firstOrNull()?.let { recentBuy ->
            val intent = Intent(requireContext(), RecentOrdersActivity::class.java)
            intent.putExtra("RecentBuyOrderItem", listOfOrderItem)
            startActivity(intent)
        }
    }

    private fun setDataInRecentBuyItem() {
        binding.recentbuyitem.visibility = View.VISIBLE
        val recentOrderItem = listOfOrderItem.firstOrNull()

        recentOrderItem?.let {
            with(binding) {
                buyAgainFoodName.text = it.foodNames?.firstOrNull() ?: ""
                buyAgainFoodPrice.text = it.totalPrice?.replace("$", "RON") ?: ""
                val image = it.foodImages?.firstOrNull()

                if (image.isNullOrEmpty()) {
                    Glide.with(requireContext())
                        .load(R.drawable.menu3)
                        .into(buyAgainFoodImage)
                } else {
                    val uri = Uri.parse(image)
                    Glide.with(requireContext())
                        .load(uri)
                        .error(R.drawable.menu3)
                        .into(buyAgainFoodImage)
                }

                buyAgainRestaurantName.text = it.restaurantName

                val isOrderAccepted = it.orderAccepted
                if (isOrderAccepted) {
                    orderedStatus.background.setTint(Color.GREEN)
                } else {
                    orderedStatus.background.setTint(Color.parseColor("#FFA500"))
                }
            }
        }
    }

    private fun setPreviousBuyItemsRecyclerView() {
        val buyAgainFoodName = mutableListOf<String>()
        val buyAgainFoodPrice = mutableListOf<String>()
        val buyAgainFoodImage = mutableListOf<String>()

        val completedOrders = listOfOrderItem.filter { it.orderAccepted && it.orderDelivered }

        completedOrders.forEach { order ->
            order.foodNames?.firstOrNull()?.let { name ->
                buyAgainFoodName.add(name)
                order.foodPrices?.firstOrNull()?.let { price ->
                    buyAgainFoodPrice.add(price)
                    order.foodImages?.firstOrNull()?.let { image ->
                        buyAgainFoodImage.add(image)
                    }
                }
            }
        }

        val rv = binding.BuyAgainRecyclerView
        rv.layoutManager = LinearLayoutManager(requireContext())
        buyItemsAgainAdapter = BuyItemsAgainAdapter(
            buyAgainFoodName,
            buyAgainFoodPrice,
            buyAgainFoodImage,
            requireContext()
        )
        rv.adapter = buyItemsAgainAdapter

        buyItemsAgainAdapter.setOnItemClickListener(object : BuyItemsAgainAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                val orderDetails = completedOrders[position]
                val intent = Intent(requireContext(), OrderDetailsActivity::class.java)
                intent.putExtra("orderId", orderDetails.itemPushkey)
                startActivity(intent)
            }
        })
    }
}