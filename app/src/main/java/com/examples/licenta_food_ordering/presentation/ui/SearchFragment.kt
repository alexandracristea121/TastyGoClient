package com.examples.licenta_food_ordering.presentation.ui

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.example.licenta_food_ordering.R
import com.example.licenta_food_ordering.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {
    private lateinit var googleMap: GoogleMap
    private lateinit var binding: FragmentSearchBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            this.googleMap = googleMap
            
            googleMap.uiSettings.apply {
                isZoomControlsEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isCompassEnabled = true
                isMapToolbarEnabled = true
            }

            googleMap.apply {
                setMinZoomPreference(4f)
                setMaxZoomPreference(18f)
                
                setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        isBuildingsEnabled = false
                        isIndoorEnabled = false
                    }
                }
                
                setOnCameraIdleListener {
                    isBuildingsEnabled = true
                    isIndoorEnabled = true
                }
            }

            val defaultLocation = LatLng(45.7477, 21.2257)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))
            addRestaurantMarkers()
        }

        setupSearch()
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
            }
        })
    }

    private fun addRestaurantMarkers() {
    }
} 