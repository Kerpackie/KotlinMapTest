package com.example.maptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.maptest.ui.theme.MapTestTheme
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        setContent {
            MapTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapViewContainer(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MapViewContainer(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(15.0)
                val startPoint = GeoPoint(52.67345, -8.64706)
                val endPoint = GeoPoint(52.670904, -8.642153)
                controller.setCenter(startPoint)
                addMarker(this, startPoint, "Start Point")
                addMarker(this, endPoint, "End Point")
                addRoute(this, startPoint, endPoint)
            }
        },
        modifier = modifier
    )
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