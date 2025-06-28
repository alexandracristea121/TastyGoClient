package com.examples.licenta_food_ordering.model.chat

data class Message(
    val content: String,
    val type: Type
) {
    enum class Type {
        USER, BOT, SUGGESTION
    }
}