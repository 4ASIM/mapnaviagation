package com.example.shahabkekhushi.ui.home
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.shahabkekhushi.R
import com.example.shahabkekhushi.databinding.FragmentHomeBinding
import com.example.shahabkekhushi.ui.MyBottomSheetDialog.MyBottomSheetDialogFragment
import com.example.shahabkekhushi.ui.MyBottomSheetDialog.OnSearchResultSelectedListener
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
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
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
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
    private val client = OkHttpClient()
    private val mapboxAccessToken by lazy {
        getString(R.string.mapbox_access_token)
    }
    private val mapStyles = arrayOf(
        Style.MAPBOX_STREETS,
        Style.SATELLITE_STREETS,
        "mapbox://styles/mapbox/traffic-day-v2",
        "mapbox://styles/mapbox/satellite-streets-v11"
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

    private var selectedMode = "driving"
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.mapboxMap.loadStyle(mapStyles[currentStyleIndex])
        initPointAnnotationManager()
        val bottomSheet = MyBottomSheetDialogFragment()
        bottomSheet.setOnSearchResultSelectedListener(this)
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
        binding.showBottomSheetButton.setOnClickListener {
            val bottomSheet = MyBottomSheetDialogFragment()
            bottomSheet.setOnSearchResultSelectedListener(this)
            bottomSheet.show(parentFragmentManager, bottomSheet.tag)
        }
        binding.cvDriving.setOnClickListener {
            selectedMode = "driving"
            Log.d("HomeFragment", "Selected mode: $selectedMode")

            highlightSelectedCard(binding.cvDriving, binding.cvWalking, binding.cvCycling)

            lastKnownLocation?.let { location ->
                val origin = Point.fromLngLat(location.longitude, location.latitude)
                val destination = routePoints?.lastOrNull() ?: return@let
                fetchRoute(origin, destination, selectedMode)
            }
        }

        binding.cvWalking.setOnClickListener {
            selectedMode = "walking"
            Log.d("HomeFragment", "Selected mode: $selectedMode")

            highlightSelectedCard(binding.cvWalking, binding.cvDriving, binding.cvCycling)

            lastKnownLocation?.let { location ->
                val origin = Point.fromLngLat(location.longitude, location.latitude)
                val destination = routePoints?.lastOrNull() ?: return@let
                fetchRoute(origin, destination, selectedMode)
            }
        }

        binding.cvCycling.setOnClickListener {
            selectedMode = "cycling"
            Log.d("HomeFragment", "Selected mode: $selectedMode")

            highlightSelectedCard(binding.cvCycling, binding.cvDriving, binding.cvWalking)

            lastKnownLocation?.let { location ->
                val origin = Point.fromLngLat(location.longitude, location.latitude)
                val destination = routePoints?.lastOrNull() ?: return@let
                fetchRoute(origin, destination, selectedMode)
            }
        }

        binding.fbNearby.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_discoverFragment)
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

    private fun highlightSelectedCard(selectedCard: CardView, vararg otherCards: CardView) {

        selectedCard.setBackgroundResource(R.drawable.cardview_border)

        otherCards.forEach { card ->
            card.setBackgroundResource(R.drawable.cardview_border2)
        }
    }
    private fun showLoadingAnimation() {
        binding.loadingAnimationView.visibility = View.VISIBLE
    }

    private fun hideLoadingAnimation() {
        binding.loadingAnimationView.visibility = View.GONE
    }

    private fun addPointAnnotation() {
        val annotationApi = binding.mapView.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()

        val originalBitmap = BitmapFactory.decodeResource(resources, R.raw.mapanimation)
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 30, 30, false) // Adjust width and height as needed

        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(18.06, 59.31))
            .withIconImage(resizedBitmap)

        pointAnnotationManager.create(pointAnnotationOptions)
    }

    private fun initNavigation() {
        if (_binding == null) return


        binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
    }

    private fun initPointAnnotationManager() {
        pointAnnotationManager = binding.mapView.annotations.createPointAnnotationManager()
    }
    private fun cycleMapStyle() {
        currentStyleIndex = (currentStyleIndex + 1) % mapStyles.size
        binding.mapView.mapboxMap.loadStyleUri(mapStyles[currentStyleIndex]) { style ->
            routePoints?.let { points ->
                drawRoute(points)
            }
        }
    }

    private var routePoints: List<Point>? = null

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
            if (style.getSource("route-source") == null) {
                style.addSource(geoJsonSource("route-source") {
                    geometry(lineString)
                })
            } else {
                (style.getSource("route-source") as GeoJsonSource).geometry(lineString)
            }
            if (style.getLayer("route-layer") == null) {
                style.addLayer(lineLayer("route-layer", "route-source") {
                    lineColor("#0019e3")
                    lineWidth(5.0)
                })
            }
        }
        routePoints = points
    }


    private fun fetchRoute(origin: Point, destination: Point, mode: String) {
        showLoadingAnimation()
        val url = "https://api.mapbox.com/directions/v5/mapbox/$mode/" +
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

                            val distanceInMeters = route.getDouble("distance")
                            val durationInSeconds = route.getDouble("duration")

                            val distanceInKm = distanceInMeters / 1000.0
                            val durationInMinutes = durationInSeconds / 60.0
                            val hours = durationInMinutes.toInt() / 60
                            val minutes = (durationInMinutes % 60).toInt()
                            val distanceText = String.format("%.2f km", distanceInKm)
                            val durationText = if (hours > 0) {
                                String.format("%d hr %d min", hours, minutes)
                            } else {
                                String.format("%d min", minutes)
                            }
                            requireActivity().runOnUiThread {
                                binding.distanceTextView.text = distanceText
                                binding.timeTextView.text = durationText

                                binding.distanceTextView.visibility = View.VISIBLE
                                binding.timeTextView.visibility = View.VISIBLE
                            }

                            val points = mutableListOf<Point>()
                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                points.add(Point.fromLngLat(coord.getDouble(0), coord.getDouble(1)))
                            }

                            routePoints = points
                            requireActivity().runOnUiThread {
                                hideLoadingAnimation()
                                drawRoute(points)

                            }
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
        lastKnownLocation?.let { currentLocation ->
            val origin = Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
            fetchRoute(origin, destination, selectedMode)
        }

        pointAnnotationManager?.deleteAll()
        val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.red_marker)
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 40, 50, false)
        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(destination)
            .withIconImage(resizedBitmap)

        pointAnnotationManager?.create(pointAnnotationOptions)

        val cameraOptions = CameraOptions.Builder()
            .center(destination)
            .zoom(14.0)
            .build()

        binding.mapView.camera.easeTo(
            cameraOptions,
            MapAnimationOptions.Builder().duration(1500L).build()
        )
    }
    override fun onAttach(context: Context) {
        super.onAttach(context)
        MapboxNavigationApp.setup {
            NavigationOptions.Builder(context).build()
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        pointAnnotationManager?.deleteAll()
//        mapboxNavigation.stopTripSession()
        _binding = null
    }
}

private fun GeoJsonSource.setGeoJson(geoJson: LineString?) {

}