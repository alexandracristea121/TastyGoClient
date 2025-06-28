package com.examples.licenta_food_ordering.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.licenta_food_ordering.databinding.FragmentCongratsBottomSheetBinding
import com.examples.licenta_food_ordering.presentation.activity.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class OrderSuccessBottomSheet : BottomSheetDialogFragment() {
    private lateinit var binding  : FragmentCongratsBottomSheetBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding= FragmentCongratsBottomSheetBinding.inflate(layoutInflater, container, false)
        binding.goHome.setOnClickListener{
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
        }
        return binding.root
    }

    companion object {

    }
}