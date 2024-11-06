package com.example.shahabkekhushi.ui.home

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
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class HomeFragment : Fragment(), OnSearchResultSelectedListener {

    private val navigationLocationProvider = NavigationLocationProvider()
    private var lastKnownLocation: com.mapbox.common.location.Location? = null
    private val client = OkHttpClient() // Initialize OkHttpClient

    private val mapboxAccessToken by lazy {
        getString(R.string.mapbox_access_token) // Retrieve the Mapbox access token from resources
    }
    private val mapStyles = arrayOf(
        Style.MAPBOX_STREETS,
        Style.SATELLITE_STREETS,
        "mapbox://styles/mapbox/traffic-day-v2", // Custom URI
        "mapbox://styles/mapbox/satellite-streets-v11" // Custom URI
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
        // Switch to the next style in the array
        currentStyleIndex = (currentStyleIndex + 1) % mapStyles.size

        // Apply the new style
        binding.mapView.mapboxMap.loadStyleUri(mapStyles[currentStyleIndex]) { style ->
            // After the style is loaded, re-draw the route if it exists
            routePoints?.let { points ->
                drawRoute(points) // Redraw the route on the new style
            }
        }
    }

    private var routePoints: List<Point>? = null

//    private fun cycleMapStyle() {
//        currentStyleIndex = (currentStyleIndex + 1) % mapStyles.size
//        binding.mapView.mapboxMap.loadStyle(mapStyles[currentStyleIndex])
//    }

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

    private fun drawRoute(points: List<Point>) {
        val lineString = LineString.fromLngLats(points)
        binding.mapView.mapboxMap.getStyle { style ->
            // Check if the source already exists
            if (style.getSource("route-source") == null) {
                style.addSource(geoJsonSource("route-source") {
                    geometry(lineString)
                })
            } else {
                // If source exists, update the geometry of the existing source
                (style.getSource("route-source") as GeoJsonSource).setGeoJson(lineString)
            }

            // Check if the layer exists, if not, add it
            if (style.getLayer("route-layer") == null) {
                style.addLayer(lineLayer("route-layer", "route-source") {
                    lineColor("#0019e3")
                    lineWidth(5.0)
                })
            }
        }
        // Store the points to re-draw later when the style changes
        routePoints = points
    }




    private fun fetchRoute(origin: Point, destination: Point) {
        // Remove the old route source and layer if they exist
        binding.mapView.mapboxMap.getStyle { style ->
            // Make sure the route source and layer are cleared before fetching the new route
            val source = style.getSource("route-source")
            if (source != null) {
                style.removeStyleSource("route-source")
            }

            val layer = style.getLayer("route-layer")
            if (layer != null) {
                style.removeStyleLayer("route-layer")
            }
        }

        // Fetch the new route
        val url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                "${origin.longitude()},${origin.latitude()};" +
                "${destination.longitude()},${destination.latitude()}" +
                "?alternatives=true&geometries=geojson&language=en&overview=full&steps=true&access_token=$mapboxAccessToken"

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HomeFragment", "Error fetching route: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        val json = JSONObject(it)
                        val routes = json.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")
                            val points = mutableListOf<Point>()
                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                points.add(Point.fromLngLat(coord.getDouble(0), coord.getDouble(1)))
                            }
                            // Draw the route and store the points
                            drawRoute(points)
                        }
                    }
                } else {
                    Log.e("HomeFragment", "Request failed: ${response.code} ${response.message}")
                }
            }
        })
    }




    override fun onSearchResultSelected(latitude: Double, longitude: Double) {
        val destination = Point.fromLngLat(longitude, latitude)

        // Fetch the route from the current location to the destination
        lastKnownLocation?.let { currentLocation ->
            val origin = Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
            fetchRoute(origin, destination)
        }

        // Clear any previous annotations
        pointAnnotationManager?.deleteAll()

        // Load the icon as a bitmap and resize it
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 40, 50, false)

        // Create a new PointAnnotationOptions with the search result coordinates
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(destination)
            .withIconImage(resizedBitmap)

        // Add the annotation to the map
        pointAnnotationManager?.create(pointAnnotationOptions)

        // Update the camera to center on the searched location
        val cameraOptions = CameraOptions.Builder()
            .center(destination)
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

private fun GeoJsonSource.setGeoJson(geoJson: LineString?) {

}
