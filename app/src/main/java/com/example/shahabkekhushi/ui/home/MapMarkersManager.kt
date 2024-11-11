package com.example.shahabkekhushi.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.shahabkekhushi.R
import com.example.shahabkekhushi.ui.home.DiscoverFragment.Companion.MARKERS_INSETS
import com.example.shahabkekhushi.ui.home.DiscoverFragment.Companion.MARKERS_INSETS_OPEN_CARD
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.search.discover.DiscoverResult

private class MapMarkersManager(private val mapView: MapView) {

    private val annotations = mutableMapOf<String, DiscoverResult>()
    private val mapboxMap: MapboxMap = mapView.getMapboxMap()
    private val pointAnnotationManager = mapView.annotations.createPointAnnotationManager(null)

    // Helper function to convert drawable to bitmap
    private fun getBitmapFromDrawable(drawableRes: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(mapView.context, drawableRes) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    // Use the helper function to get the bitmap for the location pin
    private val pinBitmap = getBitmapFromDrawable(R.drawable.red_marker)

    var onResultClickListener: ((DiscoverResult) -> Unit)? = null

    init {
        if (pinBitmap == null) {
            Log.e("MapMarkersManager", "Error: R.drawable.location could not be converted to a Bitmap.")
        }

        pointAnnotationManager.addClickListener {
            annotations[it.id]?.let { result ->
                onResultClickListener?.invoke(result)
            }
            true
        }
    }

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
        if (results.isEmpty() || pinBitmap == null) {
            return
        }

        val coordinates = ArrayList<com.mapbox.geojson.Point>(results.size)
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
