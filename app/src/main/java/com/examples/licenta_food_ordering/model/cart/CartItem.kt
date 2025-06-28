package com.examples.licenta_food_ordering.model.cart

data class CartItem(
    var foodName: String?=null,
    var foodPrice: String?=null,
    var foodDescription: String?=null,
    var foodImage: String?=null,
    var foodQuantity: Int?=null,
    var foodIngredient: String?=null
)
