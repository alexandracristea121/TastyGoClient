package com.examples.licenta_food_ordering.model.user

data class UserModel(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val phone: String? = null,
    val address: String? = null,

    val cardHolderName: String? = null,
    val cardNumber: String? = null,
    val expiryDate: String? = null,
    val cvv: String? = null
)