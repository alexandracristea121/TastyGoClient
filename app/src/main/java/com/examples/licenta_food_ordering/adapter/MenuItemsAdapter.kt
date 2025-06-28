package com.examples.licenta_food_ordering.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.MenuItemBinding
import com.examples.licenta_food_ordering.model.food.MenuItem

@Suppress("DEPRECATION")
class MenuItemsAdapter(
    private var menuItems: List<MenuItem>,
    private val context: Context,
    private val onItemClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuItemsAdapter.MenuViewHolder>() {

    private val glideOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.food_placeholder)
        .error(R.drawable.food_placeholder)
        .centerCrop()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(items: List<MenuItem>) {
        menuItems = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = MenuItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(menuItems[position])
    }

    override fun getItemCount(): Int = menuItems.size

    inner class MenuViewHolder(private val binding: MenuItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(menuItems[position])
                }
            }
        }

        fun bind(menuItem: MenuItem) = with(binding) {
            val isSectionHeader = menuItem.foodName?.startsWith("--") == true

            menuFoodName.text = menuItem.foodName.orEmpty()
            val rawPrice = menuItem.foodPrice.orEmpty().replace(",", ".")
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
            menuPrice.text = formattedPrice

            menuPrice.visibility = if (isSectionHeader) View.GONE else View.VISIBLE
            menuImage.visibility = if (isSectionHeader) View.GONE else View.VISIBLE

            if (!isSectionHeader) {
                if (!menuItem.foodImage.isNullOrEmpty()) {
                    try {
                        val uri = Uri.parse(menuItem.foodImage)
                        Glide.with(context)
                            .load(uri)
                            .apply(glideOptions)
                            .thumbnail(0.1f)
                            .into(menuImage)
                    } catch (e: Exception) {
                        menuImage.setImageResource(R.drawable.food_placeholder)
                    }
                } else {
                    menuImage.setImageResource(R.drawable.food_placeholder)
                }
            }
        }
    }
}