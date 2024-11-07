package com.example.shahabkekhushi.ui.MyBottomSheetDialog

import android.Manifest.permission
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.shahabkekhushi.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mapbox.search.*
import com.mapbox.search.offline.OfflineResponseInfo
import com.mapbox.search.offline.OfflineSearchEngine
import com.mapbox.search.offline.OfflineSearchEngineSettings
import com.mapbox.search.offline.OfflineSearchResult
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.ui.adapter.engines.SearchEngineUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.SearchResultsView

interface OnSearchResultSelectedListener {
    fun onSearchResultSelected(latitude: Double, longitude: Double)
}

class MyBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var onSearchResultSelectedListener: OnSearchResultSelectedListener? = null
    private val REQUEST_ENABLE_LOCATION = 1
    private val PERMISSIONS_REQUEST_LOCATION = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val queryEditText = view.findViewById<EditText>(R.id.query_edit_text)
        val searchResultsView = view.findViewById<SearchResultsView>(R.id.search_results_view)

        searchResultsView.initialize(
            SearchResultsView.Configuration(
                commonConfiguration = CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)
            )
        )

        val searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
            apiType = ApiType.GEOCODING,
            settings = SearchEngineSettings()
        )
        val offlineSearchEngine = OfflineSearchEngine.create(
            OfflineSearchEngineSettings()
        )

        val searchEngineUiAdapter = SearchEngineUiAdapter(
            view = searchResultsView,
            searchEngine = searchEngine,
            offlineSearchEngine = offlineSearchEngine
        )

        searchEngineUiAdapter.addSearchListener(object : SearchEngineUiAdapter.SearchListener {

            private fun showToast(message: String) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }

            override fun onSearchResultSelected(searchResult: SearchResult, responseInfo: ResponseInfo) {
                showToast("SearchResult clicked: ${searchResult.name}")
                val latitude = searchResult.coordinate.latitude()
                val longitude = searchResult.coordinate.longitude()
                onSearchResultSelectedListener?.onSearchResultSelected(latitude, longitude)
            }

            override fun onSuggestionsShown(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {}
            override fun onSearchResultsShown(suggestion: SearchSuggestion, results: List<SearchResult>, responseInfo: ResponseInfo) {}
            override fun onOfflineSearchResultsShown(results: List<OfflineSearchResult>, responseInfo: OfflineResponseInfo) {}
            override fun onSuggestionSelected(searchSuggestion: SearchSuggestion): Boolean = false
            override fun onOfflineSearchResultSelected(searchResult: OfflineSearchResult, responseInfo: OfflineResponseInfo) {}
            override fun onError(e: Exception) {
                showToast("Error happened: $e")
            }
            override fun onHistoryItemClick(historyRecord: HistoryRecord) {
                showToast("HistoryRecord clicked: ${historyRecord.name}")
            }
            override fun onPopulateQueryClick(suggestion: SearchSuggestion, responseInfo: ResponseInfo) {
                queryEditText.setText(suggestion.name)
            }
            override fun onFeedbackItemClick(responseInfo: ResponseInfo) {}
        })

        // Add TextWatcher to trigger search when text changes
        queryEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.isNotEmpty()) {
                    searchEngineUiAdapter.search(s.toString())
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(e: Editable) {}
        })

        if (!isPermissionGranted(permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(permission.ACCESS_FINE_LOCATION, permission.ACCESS_COARSE_LOCATION),
                PERMISSIONS_REQUEST_LOCATION
            )
            checkAndPromptForLocation()
        } else {
            checkAndPromptForLocation()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.behavior?.apply {
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
            halfExpandedRatio = 0.5f
            isHideable = false
        }
    }

    // Function to check if location is enabled
    private fun checkAndPromptForLocation() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivityForResult(intent, REQUEST_ENABLE_LOCATION)
        }
    }

    // Function to check permission status
    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(), "Location permission granted", Toast.LENGTH_SHORT).show()
                    checkAndPromptForLocation()  // Check if location is enabled after permission is granted
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Handle result of location settings prompt
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_LOCATION) {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(requireContext(), "Location is still disabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Location is now enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to set the listener for search result selection
    fun setOnSearchResultSelectedListener(listener: OnSearchResultSelectedListener) {
        this.onSearchResultSelectedListener = listener
    }
}
