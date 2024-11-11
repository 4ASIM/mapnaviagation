package com.example.shahabkekhushi.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.shahabkekhushi.R
import com.example.shahabkekhushi.databinding.FragmentDiscoverBinding
import com.mapbox.android.gestures.Utils.dpToPx
import com.mapbox.common.location.LocationProvider
import com.mapbox.common.location.LocationServiceFactory
import com.mapbox.geojson.BoundingBox
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.utils.internal.toPoint
import com.mapbox.search.common.DistanceCalculator
import com.mapbox.search.discover.Discover
import com.mapbox.search.discover.DiscoverAddress
import com.mapbox.search.discover.DiscoverOptions
import com.mapbox.search.discover.DiscoverQuery
import com.mapbox.search.discover.DiscoverResult
import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchResultType
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.place.SearchPlace
import com.mapbox.search.ui.view.place.SearchPlaceBottomSheetView
import java.util.UUID

class DiscoverFragment : Fragment() {

    private lateinit var discover: Discover
    private lateinit var locationProvider: LocationProvider

    private lateinit var binding: FragmentDiscoverBinding
    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapMarkersManager: MapMarkersManager

    private lateinit var searchPlaceView: SearchPlaceBottomSheetView

    private val categoryMap = mapOf(
        "Museums" to DiscoverQuery.Category.MUSEUMS,
        "Coffee Shop" to DiscoverQuery.Category.COFFEE_SHOP_CAFE,
        "Restaurant" to DiscoverQuery.Category.RESTAURANTS,
        "Bar" to DiscoverQuery.Category.BARS,
        "Hotel" to DiscoverQuery.Category.HOTEL,
        "Hospital" to DiscoverQuery.Category.HOSPITAL
    )

    private fun defaultDeviceLocationProvider(): LocationProvider =
        LocationServiceFactory.getOrCreate()
            .getDeviceLocationProvider(null)
            .value
            ?: throw Exception("Failed to get device location provider")

    private fun Context.showToast(@StringRes resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    private fun Context.isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false)

        discover = Discover.create()
        locationProvider = defaultDeviceLocationProvider()

        val mapView: MapView = binding.mapView
        mapMarkersManager = MapMarkersManager(mapView)
        mapView.getMapboxMap().also { mapboxMap ->
            this.mapboxMap = mapboxMap

            mapboxMap.loadStyle(Style.MAPBOX_STREETS) {
                mapView.location.updateSettings {
                    enabled = true
                }

                mapView.location.addOnIndicatorPositionChangedListener(object : OnIndicatorPositionChangedListener {
                    override fun onIndicatorPositionChanged(point: Point) {
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(point)
                                .zoom(14.0)
                                .build()
                        )

                        mapView.location.removeOnIndicatorPositionChangedListener(this)
                    }
                })
            }
        }

        fun searchNearbyPlaces(category: String) {
            val categoryQuery = categoryMap[category]
            if (categoryQuery != null) {
                locationProvider.getLastLocation { location ->
                    if (location == null) return@getLastLocation

                    lifecycleScope.launchWhenStarted {
                        val response = discover.search(
                            query = categoryQuery,
                            proximity = location.toPoint(),
                            options = DiscoverOptions(limit = 20)
                        )

                        response.onValue { results ->
                            mapMarkersManager.showResults(results)
                        }.onError { e ->
                            Log.d("DiscoverApiExample", "Error during search request", e)
                            requireContext().showToast(R.string.discover_search_error)
                        }
                    }
                }
            } else {
                requireContext().showToast(R.string.discover_search_error)
            }
        }


        val MuseumButton = binding.cvBanks
        val coffeeShopButton = binding.cvBanks
        val restaurantButton = binding.cvRestaurant
        val barButton = binding.cvHospital
        val hotelButton = binding.cvHotel
        val hospitalButton = binding.ivHospital

        val searchButtons = listOf(
            "Museums" to DiscoverQuery.Category.MUSEUMS,
            "Coffee Shop" to DiscoverQuery.Category.COFFEE_SHOP_CAFE,

            coffeeShopButton to "Coffee Shop",
            restaurantButton to "Restaurant",
            barButton to "Bar",
            hotelButton to "Hotel",
            hospitalButton to "Hospital"
        )

        searchButtons.forEach { (button, category) ->
            button.setOnClickListener {
                searchNearbyPlaces(category)
            }
        }






        searchPlaceView = binding.searchPlaceView.apply {
            initialize(CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL))
            isFavoriteButtonVisible = false
            addOnCloseClickListener {
                mapMarkersManager.adjustMarkersForClosedCard()
                hide()
            }
        }

        mapMarkersManager.onResultClickListener = { result ->
            mapMarkersManager.adjustMarkersForOpenCard()
            searchPlaceView.open(result.toSearchPlace())
            locationProvider.userDistanceTo(result.coordinate) { distance ->
                distance?.let { searchPlaceView.updateDistance(distance) }
            }
        }

        if (!requireContext().isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSIONS_REQUEST_LOCATION
            )
        }

        return binding.root
    }

    private fun LocationProvider.userDistanceTo(destination: Point, callback: (Double?) -> Unit) {
        getLastLocation { location ->
            if (location == null) {
                callback(null)
            } else {
                val distance = DistanceCalculator.instance(latitude = location.latitude)
                    .distance(location.toPoint(), destination)
                callback(distance)
            }
        }
    }

    private class MapMarkersManager(mapView: MapView) {

        private val annotations = mutableMapOf<String, DiscoverResult>()
        private val mapboxMap: MapboxMap = mapView.getMapboxMap()
        private val pointAnnotationManager = mapView.annotations.createPointAnnotationManager(null)
        private val pinBitmap = mapView.context.bitmapFromDrawableRes(R.drawable.red_marker)

        var onResultClickListener: ((DiscoverResult) -> Unit)? = null

        init {
            pointAnnotationManager.addClickListener {
                annotations[it.id]?.let { result ->
                    onResultClickListener?.invoke(result)
                }
                true
            }
        }

        private fun Context.bitmapFromDrawableRes(@DrawableRes resId: Int): Bitmap =
            BitmapFactory.decodeResource(resources, resId)

        fun clearMarkers() {
            pointAnnotationManager.deleteAll()
            annotations.clear()
        }

        fun adjustMarkersForOpenCard() {
            val coordinates = annotations.values.map { it.coordinate }
            mapboxMap.cameraForCoordinates(
                coordinates, CameraOptions.Builder().build(), MARKERS_INSETS_OPEN_CARD, null, null
            ) {
                mapboxMap.setCamera(it)
            }
        }

        fun adjustMarkersForClosedCard() {
            val coordinates = annotations.values.map { it.coordinate }
            mapboxMap.cameraForCoordinates(
                coordinates, CameraOptions.Builder().build(), MARKERS_INSETS, null, null
            ) {
                mapboxMap.setCamera(it)
            }
        }

        fun showResults(results: List<DiscoverResult>) {
            clearMarkers()
            if (results.isEmpty()) return

            val coordinates = ArrayList<Point>(results.size)
            results.forEach { result ->
                val options = PointAnnotationOptions()
                    .withPoint(result.coordinate)
                    .withIconImage(pinBitmap)
                    .withIconAnchor(IconAnchor.BOTTOM)

                val annotation = pointAnnotationManager.create(options)
                annotations[annotation.id] = result
                coordinates.add(result.coordinate)
            }

            mapboxMap.cameraForCoordinates(
                coordinates, CameraOptions.Builder().build(), MARKERS_INSETS, null, null
            ) {
                mapboxMap.setCamera(it)
            }
        }
    }

    companion object {
        const val PERMISSIONS_REQUEST_LOCATION = 0

        val MARKERS_BOTTOM_OFFSET = dpToPx(176f).toDouble()
        val MARKERS_EDGE_OFFSET = dpToPx(64f).toDouble()
        val PLACE_CARD_HEIGHT = dpToPx(300f).toDouble()

        val MARKERS_INSETS = EdgeInsets(
            MARKERS_EDGE_OFFSET, MARKERS_EDGE_OFFSET, MARKERS_BOTTOM_OFFSET, MARKERS_EDGE_OFFSET
        )

        val MARKERS_INSETS_OPEN_CARD = EdgeInsets(
            MARKERS_EDGE_OFFSET, MARKERS_EDGE_OFFSET, PLACE_CARD_HEIGHT, MARKERS_EDGE_OFFSET
        )

        fun DiscoverAddress.toSearchAddress(): SearchAddress {
            return SearchAddress(
                houseNumber = houseNumber,
                street = street,
                neighborhood = neighborhood,
                locality = locality,
                postcode = postcode,
                place = place,
                district = district,
                region = region,
                country = country
            )
        }
        fun CoordinateBounds.toBoundingBox(): BoundingBox {
            return BoundingBox.fromLngLats(southwest.longitude(), southwest.latitude(), northeast.longitude(), northeast.latitude())
        }
        fun DiscoverResult.toSearchPlace(): SearchPlace {
            return SearchPlace(
                id = name + UUID.randomUUID().toString(),
                name = name,
                descriptionText = null,
                address = address.toSearchAddress(),
                resultTypes = listOf(SearchResultType.POI),
                record = null,
                coordinate = coordinate,
                routablePoints = routablePoints,
                categories = categories,
                makiIcon = makiIcon,
                metadata = null,
                distanceMeters = null,
                feedback = null,
            )
        }
    }
}
