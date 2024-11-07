package com.example.shahabkekhushi.ui.slideshow

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.shahabkekhushi.R
import com.example.shahabkekhushi.api
import com.example.shahabkekhushi.databinding.FragmentHomeBinding
import com.example.shahabkekhushi.ui.MyBottomSheetDialog.MyBottomSheetDialogFragment
import com.example.shahabkekhushi.ui.MyBottomSheetDialog.OnSearchResultSelectedListener
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SlideshowFragment : Fragment(), OnSearchResultSelectedListener {

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

        initPointAnnotationManager()

//        val bottomSheet = MyBottomSheetDialogFragment()
//        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
//
//        binding.showBottomSheetButton.setOnClickListener {
//            val bottomSheet = MyBottomSheetDialogFragment()
//            bottomSheet.show(parentFragmentManager, bottomSheet.tag)
//        }
        val bottomSheet = MyBottomSheetDialogFragment()
        bottomSheet.setOnSearchResultSelectedListener(this)
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)

        binding.showBottomSheetButton.setOnClickListener {
            val bottomSheet = MyBottomSheetDialogFragment()
            bottomSheet.setOnSearchResultSelectedListener(this)
            bottomSheet.show(parentFragmentManager, bottomSheet.tag)
        }



        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initNavigation()

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
                mapboxNavigation.registerLocationObserver(object : LocationObserver {
                    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                        val enhancedLocation = locationMatcherResult.enhancedLocation
                        navigationLocationProvider.changePosition(
                            enhancedLocation,
                            locationMatcherResult.keyPoints
                        )
                        lastKnownLocation = enhancedLocation
                        updateCamera(enhancedLocation)
                        mapboxNavigation.unregisterLocationObserver(this)
                    }

                    override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}
                })
                mapboxNavigation.startTripSession()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {

            }
        },
        onInitialize = this::initNavigation
    )
    private fun addPointAnnotation() {
        // Create an instance of the Annotation API and get the PointAnnotationManager
        val annotationApi = binding.mapView.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()

        // Load the icon as a bitmap and resize it
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.location_pin_svgrepo_com)
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 30, 30, false) // Adjust width and height as needed

        // Set options for the resulting point annotation
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(18.06, 59.31))
            .withIconImage(resizedBitmap)

        // Add the point annotation to the map
        pointAnnotationManager.create(pointAnnotationOptions)
    }


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
    private fun initPointAnnotationManager() {
        // Initialize PointAnnotationManager once after the style is loaded
        pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
    }
    private fun cycleMapStyle() {
        currentStyleIndex = (currentStyleIndex + 1) % mapStyles.size
        binding.mapView.mapboxMap.loadStyle(mapStyles[currentStyleIndex])
    }

    private fun updateCamera(location: com.mapbox.common.location.Location) {
        val point = Point.fromLngLat(location.longitude, location.latitude)
        val cameraOptions = CameraOptions.Builder()
            .center(point)
            .zoom(14.0)
            .build()

        binding.mapView.camera.easeTo(
            cameraOptions,
            MapAnimationOptions.Builder().duration(1500L).build()
        )
    }

    private var pointAnnotationManager: PointAnnotationManager? = null



    override fun onSearchResultSelected(latitude: Double, longitude: Double) {
        val startPoint = Point.fromLngLat(lastKnownLocation?.longitude ?: 0.0, lastKnownLocation?.latitude ?: 0.0)
        val endPoint = Point.fromLngLat(longitude, latitude)

        // Clear previous annotations
        pointAnnotationManager?.deleteAll()

        // Add the destination point annotation
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 40, 50, false)
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(endPoint)
            .withIconImage(resizedBitmap)

        pointAnnotationManager?.create(pointAnnotationOptions)

        // Request directions from current location to selected location
        CoroutineScope(Dispatchers.IO).launch {
            val data = api.builder.getDirections(
                startPoint.latitude().toString(),
                startPoint.longitude().toString(),
                endPoint.latitude().toString(),
                endPoint.longitude().toString(),
                "sk.eyJ1IjoiYWxpMTEyMjMzNDQiLCJhIjoiY20yeWR1NWx1MDBqeDJxczU5dHM5dW9sNiJ9.Zf5eESNgDLxQF2ORhvIv1g"
            )

            if (data.isSuccessful) {
                Log.v("whatstheerror", data.raw().toString())
                launch(Dispatchers.Main) {
                    val annotationApi = binding.mapView.annotations
                    val polylineAnnotationManager = annotationApi!!.createPolylineAnnotationManager()
                    val points2 = arrayListOf<Point>()

                    if (data.body()!!.routes.isNotEmpty()) {
                        val coordinates = data.body()!!.routes[0]!!.geometry.coordinates
                        Log.v("RouteCoordinates", coordinates.toString())
                        for (i in coordinates.indices) {
                            points2.add(Point.fromLngLat(coordinates[i][0], coordinates[i][1]))
                        }

                        val polylineAnnotationOptions = PolylineAnnotationOptions()
                            .withPoints(points2)
                            .withLineColor("#ee4e8b")
                            .withLineWidth(1.0)

                        polylineAnnotationManager?.create(polylineAnnotationOptions)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "No route, Try again",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Move camera to the new location with animation
        val cameraOptions = CameraOptions.Builder()
            .center(endPoint)
            .zoom(14.0)
            .build()

        binding.mapView.camera.easeTo(
            cameraOptions,
            MapAnimationOptions.Builder().duration(1500L).build()
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        pointAnnotationManager?.deleteAll() // Clean up annotations
        _binding = null
    }
}