package com.examples.licenta_food_ordering.presentation.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.licenta_food_ordering.databinding.FragmentProfileBinding
import com.examples.licenta_food_ordering.model.user.UserModel
import com.examples.licenta_food_ordering.presentation.activity.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(layoutInflater, container, false)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        setupExpiryDateInput()
        loadUserData()

        binding.apply {
            name.isEnabled = false
            email.isEnabled = false
            address.isEnabled = false
            phone.isEnabled = false

            editButton.setOnClickListener {
                if (!name.isEnabled) {
                    name.isEnabled = true
                    email.isEnabled = true
                    address.isEnabled = true
                    phone.isEnabled = true
                    editButton.text = "Save Changes"
                    Toast.makeText(requireContext(), "Editing Profile", Toast.LENGTH_SHORT).show()
                } else {
                    saveUserInfo()
                    name.isEnabled = false
                    email.isEnabled = false
                    address.isEnabled = false
                    phone.isEnabled = false
                    editButton.text = "Edit Profile"
                    Toast.makeText(requireContext(), "Profile Updated", Toast.LENGTH_SHORT).show()
                }
            }

            logoutButton.setOnClickListener {
                logout()
            }
        }

        return binding.root
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userReference = database.getReference("user").child(userId)
            userReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val userProfile = snapshot.getValue(UserModel::class.java)
                        if (userProfile != null) {
                            binding.name.setText(userProfile.name)
                            binding.email.setText(userProfile.email)
                            binding.address.setText(userProfile.address ?: "")
                            binding.phone.setText(userProfile.phone ?: "")
                            binding.cardHolderName.setText(userProfile.cardHolderName ?: "")
                            binding.cardNumber.setText(userProfile.cardNumber ?: "")
                            binding.expiryDate.setText(userProfile.expiryDate ?: "")
                            binding.cvv.setText(userProfile.cvv ?: "")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun saveUserInfo() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("user").child(userId)

        val userData = hashMapOf(
            "name" to binding.name.text.toString(),
            "email" to binding.email.text.toString(),
            "phone" to binding.phone.text.toString(),
            "address" to binding.address.text.toString(),
            "cardHolderName" to binding.cardHolderName.text.toString(),
            "cardNumber" to binding.cardNumber.text.toString(),
            "expiryDate" to binding.expiryDate.text.toString(),
            "cvv" to binding.cvv.text.toString()
        )

        userRef.updateChildren(userData as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun setupExpiryDateInput() {
        val expiryEditText = binding.expiryDate
        expiryEditText.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s == null) return

                val input = s.toString()
                if (input == current) return

                val clean = input.replace("\\D".toRegex(), "")
                val cleanCurrent = current.replace("\\D".toRegex(), "")

                val cl = clean.length
                var formatted = ""

                if (clean == cleanCurrent) return

                val mm = if (cl >= 2) clean.substring(0, 2) else clean
                val dd = if (cl >= 4) clean.substring(2, 4) else if (cl > 2) clean.substring(2) else ""
                val yyyy = if (cl > 4) clean.substring(4).take(4) else ""

                if (mm.length == 2) {
                    val month = mm.toInt()
                    if (month < 1 || month > 12) {
                        expiryEditText.error = "Invalid month"
                        return
                    }
                    formatted += "$mm/"
                } else {
                    formatted += mm
                }

                if (dd.length == 2) {
                    val day = dd.toInt()
                    if (day < 1 || day > 31) {
                        expiryEditText.error = "Invalid day"
                        return
                    }
                    formatted += "$dd/"
                } else {
                    formatted += dd
                }

                formatted += yyyy
                current = formatted
                expiryEditText.setText(formatted)
                expiryEditText.setSelection(formatted.length.coerceAtMost(expiryEditText.text?.length
                    ?: 0))
            }
        })
    }
}