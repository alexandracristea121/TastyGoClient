package com.examples.licenta_food_ordering.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.licenta_food_ordering.databinding.ItemChatBinding
import com.examples.licenta_food_ordering.model.chat.Message

class ChatAdapter(
    private val messageList: List<Message>
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_BOT = 1
        private const val TYPE_SUGGESTION = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (messageList[position].type) {
            Message.Type.USER -> TYPE_USER
            Message.Type.BOT -> TYPE_BOT
            Message.Type.SUGGESTION -> TYPE_SUGGESTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messageList[position])
    }

    override fun getItemCount(): Int = messageList.size

    inner class ChatViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            with(binding) {
                userMessageTextView.visibility = View.GONE
                botMessageTextView.visibility = View.GONE

                when (message.type) {
                    Message.Type.USER -> {
                        userMessageTextView.visibility = View.VISIBLE
                        userMessageTextView.text = "Utilizator: ${message.content}"
                    }
                    Message.Type.BOT -> {
                        botMessageTextView.visibility = View.VISIBLE
                        botMessageTextView.text = "FoodBot: ${message.content}"
                    }
                    Message.Type.SUGGESTION -> {
                        botMessageTextView.visibility = View.VISIBLE
                        botMessageTextView.text = "Sugestie: ${message.content}"
                    }
                }
            }
        }
    }
}