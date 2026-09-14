package ru.mukapro.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapView
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView as LegacyMapView
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchListener
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchSession
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouteListener
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.RequestPoint
import com.yandex.mapkit.directions.driving.RequestPointType
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.runtime.Error
import com.yandex.mapkit.map.ImageProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.MAPKIT_API_KEY.isNotBlank()) MapKitFactory.initialize(this)
        setContent { MukaproApp() }
    }
}

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
    val point: Point
)

private val demoParkings = listOf(
    ParkingPlace("p1", "Бесплатное место на улице", "FREE", "420 м", "6 мин", 84, 92, "0 ₽", "Без шлагбаума", Point(55.7641, 37.5907)),
    ParkingPlace("p2", "Двор без шлагбаума", "COURTYARD", "180 м", "3 мин", 71, 86, "0 ₽", "Вероятно доступен", Point(55.7650, 37.5922)),
    ParkingPlace("p3", "Платная парковка", "PAID", "80 м", "1 мин", 96, 74, "450 ₽/ч", "Открытая", Point(55.7655, 37.5940)),
    ParkingPlace("p4", "Бесплатный карман", "FREE", "650 м", "8 мин", 62, 73, "0 ₽", "Без шлагбаума", Point(55.7630, 37.5882))
)

@Composable
fun MukaproApp() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("Патриаршие пруды") }
    var filter by remember { mutableStateOf("ALL") }
    var selected by remember { mutableStateOf<ParkingPlace?>(null) }
    var status by remember { mutableStateOf("Готовы найти место") }
    var destination by remember { mutableStateOf(Point(55.7650, 37.5930)) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var searchManager by remember { mutableStateOf<SearchManager?>(null) }
    var searchSession by remember { mutableStateOf<SearchSession?>(null) }
    var route by remember { mutableStateOf<DrivingRoute?>(null) }

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
            if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
                SetupCard()
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).also { mv ->
                                mapViewRef = mv
                                searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
                                mv.map.move(CameraPosition(Point(55.7650, 37.5930), 14.5f, 0f, 0f))
                                drawDemoMap(mv, demoParkings, destination)
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
                                val mv = mapViewRef ?: return@Button
                                val manager = searchManager ?: return@Button
                                val options = SearchOptions().apply {
                                    searchTypes = SearchType.GEO.value
                                    resultPageSize = 5
                                    geometry = true
                                }
                                status = "Ищем: $query"
                                searchSession = manager.submit(
                                    query,
                                    Geometry.fromPoint(destination),
                                    options,
                                    object : SearchListener {
                                        override fun onSearchResponse(response: Response) {
                                            val geo = response.collection.children.mapNotNull { it.obj }.firstOrNull()
                                            val point = geo?.geometry?.firstNotNullOfOrNull { it.point }
                                            if (point != null) {
                                                destination = point
                                                mv.map.move(CameraPosition(point, 15.5f, 0f, 0f), Animation(Animation.Type.SMOOTH, 0.5f), null)
                                                drawDemoMap(mv, demoParkings, point)
                                                status = "Цель найдена. Выберите парковку."
                                            } else status = "Точка не найдена"
                                        }
                                        override fun onSearchError(error: Error) { status = "Ошибка поиска" }
                                    }
                                )
                            }) { Text("Ехать") }
                        }
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
                            selected = place
                            if (BuildConfig.MAPKIT_API_KEY.isNotBlank()) {
                                mapViewRef?.let { buildRoute(it, destination, place.point) { r -> route = r; status = "Маршрут построен" } }
                            }
                        },
                        onParked = {
                            status = "Парковка отмечена. Если зона платная — откройте «Парковки России»."
                            if (place.type == "PAID") {
                                val intent = context.packageManager.getLaunchIntentForPackage("ru.mos.parking")
                                if (intent != null) context.startActivity(intent) else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://parking.mos.ru/")))
                            }
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

    DisposableEffect(Unit) {
        onDispose { searchSession?.cancel(); mapViewRef?.onStop() }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("МукаПро", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Найдём место. Доведём. Поможем припарковаться.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetupCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Карта ещё не подключена", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Добавьте MAPKIT_API_KEY в local.properties и пересоберите приложение.")
            Spacer(Modifier.height(6.dp))
            Text("Демо-режим интерфейса готов; после ключа появится реальная карта Яндекс Карт.")
        }
    }
}

@Composable
private fun FilterRow(current: String, onChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("ALL" to "Все", "FREE" to "Бесплатные", "COURTYARD" to "Дворы", "PAID" to "Платные").forEach { (id, label) ->
            FilterChip(selected = current == id, onClick = { onChange(id) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ParkingCard(place: ParkingPlace, onDrive: () -> Unit, onParked: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.title, fontWeight = FontWeight.Bold)
                    Text("${place.distance} · пешком ${place.walk}")
                }
                Text("${place.score}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Вероятность: ${place.probability}% · ${place.price}")
            Text(place.access)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDrive, modifier = Modifier.weight(1f)) { Text("Ехать сюда") }
                OutlinedButton(onClick = onParked, modifier = Modifier.weight(1f)) { Text("Я припарковался") }
            }
        }
    }
}

private fun drawDemoMap(mapView: MapView, places: List<ParkingPlace>, destination: Point) {
    val objects = mapView.map.mapObjects
    objects.clear()
    val icon = ImageProvider.fromBitmap(makePinBitmap(0xFF16A34A.toInt()))
    val destinationIcon = ImageProvider.fromBitmap(makePinBitmap(0xFFDC2626.toInt()))
    places.forEach { place ->
        objects.addPlacemark().apply {
            geometry = place.point
            setIcon(icon)
        }
    }
    objects.addPlacemark().apply {
        geometry = destination
        setIcon(destinationIcon)
    }
}

private fun makePinBitmap(color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawCircle(32f, 32f, 18f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(32f, 32f, 7f, paint)
    return bitmap
}

private fun buildRoute(mapView: MapView, from: Point, to: Point, onDone: (DrivingRoute) -> Unit) {
    val router: DrivingRouter = DirectionsFactory.getInstance().createDrivingRouter()
    val points = listOf(
        RequestPoint(from, RequestPointType.WAYPOINT, null, null, null),
        RequestPoint(to, RequestPointType.WAYPOINT, null, null, null)
    )
    router.requestRoutes(
        points,
        DrivingOptions().apply { routesCount = 1 },
        VehicleOptions(),
        object : DrivingRouteListener {
            override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                val best = routes.firstOrNull() ?: return
                mapView.map.mapObjects.addPolyline(best.geometry).apply {
                    strokeWidth = 6f
                    setStrokeColor(0xFF2563EB.toInt())
                    outlineWidth = 2f
                    outlineColor = 0xFFFFFFFF.toInt()
                }
                onDone(best)
            }
            override fun onDrivingRoutesError(error: Error) { }
        }
    )
}
