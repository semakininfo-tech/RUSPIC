package ru.mukapro.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

data class ParkingPlace(
    val id: String,
    val title: String,
    val type: String,
    val distance: String,
    val walk: String,
    val probability: Int,
    val score: Int,
    val price: String,
    val access: String,
    val point: GeoPoint // Используем GeoPoint вместо Point
)

// Пример бесплатных парковок (можно заменить на загрузку из OSM через Overpass API)
private val demoParkings = listOf(
    ParkingPlace("p1", "Бесплатное место на улице", "FREE", "420 м", "6 мин", 84, 92, "0 ₽", "Без шлагбаума", GeoPoint(55.7641, 37.5907)),
    ParkingPlace("p2", "Двор без шлагбаума", "COURTYARD", "180 м", "3 мин", 71, 86, "0 ₽", "Вероятно доступен", GeoPoint(55.7650, 37.5922)),
    ParkingPlace("p3", "Платная парковка", "PAID", "80 м", "1 мин", 96, 74, "450 ₽/ч", "Открытая", GeoPoint(55.7655, 37.5940)),
    ParkingPlace("p4", "Бесплатный карман", "FREE", "650 м", "8 мин", 62, 73, "0 ₽", "Без шлагбаума", GeoPoint(55.7630, 37.5882))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MukaproApp() }
    }
}

@Composable
fun MukaproApp() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("Патриаршие пруды") }
    var filter by remember { mutableStateOf("ALL") }
    var status by remember { mutableStateOf("Готовы найти место") }
    var destination by remember { mutableStateOf(GeoPoint(55.7650, 37.5930)) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val filtered = demoParkings.filter {
        filter == "ALL" || (filter == "FREE" && it.type == "FREE") || (filter == "COURTYARD" && it.type == "COURTYARD") || (filter == "PAID" && it.type == "PAID")
    }.sortedByDescending { it.score }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header()
            
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).also { mapView ->
                            mapViewRef = mapView
                            mapView.setTileSource(TileSourceFactory.MAPNIK) // OpenStreetMap тайлы
                            mapView.setMultiTouchControls(true)
                            mapView.controller.setZoom(14.5)
                            mapView.controller.setCenter(GeoPoint(55.7650, 37.5930))
                            drawOsmMap(mapView, demoParkings, destination)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Куда едем?") }
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val mapView = mapViewRef ?: return@Button
                            status = "Поиск адреса..."
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val points = NominatimGeocoder.searchAddressGeoPoint(query)
                                    if (points.isNotEmpty()) {
                                        val point = points.first()
                                        destination = point
                                        mapView.controller.animateTo(point, 15.5, 500)
                                        drawOsmMap(mapView, demoParkings, point)
                                        status = "Цель найдена. Выберите парковку."
                                    } else {
                                        status = "Адрес не найден"
                                    }
                                } catch (e: Exception) {
                                    status = "Ошибка поиска: ${e.message}"
                                }
                            }
                        }) { Text("Ехать") }
                    }
                }
            }

            Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            FilterRow(filter) { filter = it }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered) { place ->
                    ParkingCard(
                        place = place,
                        onDrive = {
                            mapViewRef?.let { buildOsmRoute(it, destination, place.point) { status = "Маршрут построен до: ${place.title}" } }
                        },
                        onParked = {
                            status = "Парковка отмечена"
                        }
                    )
                }
                
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("GPS Engine", fontWeight = FontWeight.Bold)
                            Text("Защита от одиночных GPS-скачков включена в MVP 0.2.")
                            TextButton(onClick = { status = "GPS-скачок обнаружен → игнорируем" }) { Text("Симулировать GPS-скачок") }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

private fun drawOsmMap(mapView: MapView, parkings: List<ParkingPlace>, destination: GeoPoint) {
    mapView.overlays.clear()
    
    // Маркер цели
    val destMarker = Marker(mapView)
    destMarker.position = destination
    destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    destMarker.title = "Цель"
    mapView.overlays.add(destMarker)
    
    // Маркеры парковок
    parkings.forEach { parking ->
        val marker = Marker(mapView)
        marker.position = parking.point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = parking.title
        marker.snippet = parking.price
        mapView.overlays.add(marker)
    }
    
    mapView.invalidate()
}

private fun buildOsmRoute(mapView: MapView, from: GeoPoint, to: GeoPoint, onSuccess: () -> Unit) {
    // Используем OSRM (Open Source Routing Machine) для построения маршрута
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=full&geometries=geojson"
            val response = java.net.URL(url).readText()
            val json = org.json.JSONObject(response)
            val route = json.getJSONArray("routes").getJSONObject(0)
            val geometry = route.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            
            val points = mutableListOf<GeoPoint>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0))) // OSRM возвращает [lon, lat]
            }
            
            withContext(Dispatchers.Main) {
                val polyline = Polyline()
                polyline.setPoints(points)
                polyline.outlinePaint.color = android.graphics.Color.BLUE
                polyline.outlinePaint.strokeWidth = 8f
                mapView.overlays.add(polyline)
                mapView.invalidate()
                onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                e.printStackTrace()
            }
        }
    }
}
