package com.example.shahabkekhushi.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.shahabkekhushi.databinding.FragmentHomeBinding
import com.example.shahabkekhushi.ui.MyBottomSheetDialog.MyBottomSheetDialogFragment
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
class HomeFragment : Fragment() {

    private val navigationLocationProvider = NavigationLocationProvider()
    private var lastKnownLocation: com.mapbox.common.location.Location? = null

    private val mapStyles = arrayOf(
        Style.MAPBOX_STREETS,
        Style.SATELLITE_STREETS,
        Style.TRAFFIC_DAY,
        Style.SATELLITE,
        Style.TRAFFIC_NIGHT
    )
    private var currentStyleIndex = 0

    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                enhancedLocation,
                locationMatcherResult.keyPoints
            )
            lastKnownLocation = enhancedLocation
            updateCamera(enhancedLocation)
        }

        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.mapboxMap.loadStyle(mapStyles[currentStyleIndex])

        val bottomSheet = MyBottomSheetDialogFragment()
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)

        binding.showBottomSheetButton.setOnClickListener {
            val bottomSheet = MyBottomSheetDialogFragment()
            bottomSheet.show(parentFragmentManager, bottomSheet.tag)
        }


        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initNavigation()

        // Set click listener for recenter button
        binding.recenterButton.setOnClickListener {
            lastKnownLocation?.let { location ->
                updateCamera(location)
            }
        }

        binding.changeStyleButton.setOnClickListener {
            cycleMapStyle()
        }
    }

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.registerLocationObserver(locationObserver)
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                mapboxNavigation.unregisterLocationObserver(locationObserver)
            }
        },
        onInitialize = this::initNavigation
    )

    private fun initNavigation() {
        if (_binding == null) return
        MapboxNavigationApp.setup {
            NavigationOptions.Builder(requireContext()).build()
        }
        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
    }

    private fun cycleMapStyle() {
        currentStyleIndex = (currentStyleIndex + 1) % mapStyles.size // Cycle to next style
        binding.mapView.mapboxMap.loadStyle(mapStyles[currentStyleIndex])
    }

    private fun updateCamera(location: com.mapbox.common.location.Location) {
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(1500L).build()
        binding.mapView.camera.easeTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(location.longitude, location.latitude))
                .zoom(14.0)
                .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0))
                .build(),
            mapAnimationOptions
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}