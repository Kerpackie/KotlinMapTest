package com.example.maptest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.example.maptest.ui.theme.MapTestTheme
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val startPointState = mutableStateOf<GeoPoint?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startLocationUpdates()
            } else {
                // Handle permission denial
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startLocationUpdates()
        }

        setContent {
            MapTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapViewContainer(
                        modifier = Modifier.padding(innerPadding),
                        startPoint = startPointState.value,
                        currentCoords = startPointState.value
                    )
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10 seconds
            fastestInterval = 5000 // 5 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val startPoint = GeoPoint(location.latitude, location.longitude)
                    startPointState.value = startPoint
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun MapViewContainer(modifier: Modifier = Modifier, startPoint: GeoPoint?, currentCoords: GeoPoint?) {
    Column(modifier = modifier) {
        Text(text = "Current Coordinates: ${currentCoords?.latitude ?: "N/A"}, ${currentCoords?.longitude ?: "N/A"}")
        if (startPoint != null) {
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(15.0)
                        controller.setCenter(startPoint)
                        addMarker(this, startPoint, "Start Point")

                        val endPoint = GeoPoint(52.670904, -8.642153)
                        addMarker(this, endPoint, "End Point")
                        addRoute(this, startPoint, endPoint)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(text = "Waiting for device location...")
        }
    }
}

fun addMarker(mapView: MapView, point: GeoPoint, title: String) {
    val marker = Marker(mapView)
    marker.position = point
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    marker.title = title
    mapView.overlays.add(marker)
}

fun addRoute(mapView: MapView, startPoint: GeoPoint, endPoint: GeoPoint) {
    CoroutineScope(Dispatchers.IO).launch {
        val url = "http://router.project-osrm.org/route/v1/walking/${startPoint.longitude},${startPoint.latitude};${endPoint.longitude},${endPoint.latitude}?overview=full&geometries=geojson"
        val response = URL(url).readText()
        val json = JSONObject(response)
        val routes = json.getJSONArray("routes")
        if (routes.length() > 0) {
            val geometry = routes.getJSONObject(0).getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            val polyline = Polyline()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                val geoPoint = GeoPoint(coord.getDouble(1), coord.getDouble(0))
                polyline.addPoint(geoPoint)
            }
            withContext(Dispatchers.Main) {
                mapView.overlays.add(polyline)
                mapView.invalidate()
            }
        }
    }
}