// Добавьте эти импорты в начало файла
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MukaproApp() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("Патриаршие пруды") }
    var filter by remember { mutableStateOf("ALL") }
    var status by remember { mutableStateOf("Загрузка данных...") }
    var destination by remember { mutableStateOf(Point(55.7650, 37.5930)) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Реальные данные вместо demoParkings
    var parkings by remember { mutableStateOf<List<ParkingPlace>>(emptyList()) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // 1. Загружаем парковки из OpenStreetMap при запуске
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        
        try {
            status = "Загрузка парковок из открытых источников..."
            val loadedParkings = OsmParkingRepository.getFreeParkingsInMoscow()
            parkings = loadedParkings
            status = "Найдено ${parkings.size} бесплатных парковок. Введите адрес."
        } catch (e: Exception) {
            status = "Ошибка загрузки парковок: ${e.message}"
        }
    }

    val filtered = parkings.filter {
        filter == "ALL" || (filter == "FREE" && it.type == "FREE") || 
        (filter == "COURTYARD" && it.access.contains("Шлагбаум", ignoreCase = true).not())
    }.sortedByDescending { it.score }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header() // Предполагается, что эта функция у вас уже определена
            
            if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
                SetupCard() // Предполагается, что эта функция у вас уже определена
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).also { mv ->
                                mapViewRef = mv
                                mv.onStart()
                                mv.map.move(CameraPosition(Point(55.7650, 37.5930), 14.5f, 0f, 0f))
                                // Рисуем маркеры на карте
                                drawDemoMap(mv, parkings, destination)
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
                                status = "Поиск адреса..."
                                
                                // 2. Используем бесплатный Nominatim вместо Yandex SearchManager
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val points = NominatimGeocoder.searchAddress(query)
                                        if (points.isNotEmpty()) {
                                            val point = points.first()
                                            destination = point
                                            mv.map.move(CameraPosition(point, 15.5f, 0f, 0f), Animation(Animation.Type.SMOOTH, 0.5f), null)
                                            drawDemoMap(mv, parkings, point)
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
            }

            Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            FilterRow(filter) { filter = it } // Предполагается, что эта функция у вас уже определена

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered) { place ->
                    ParkingCard( // Предполагается, что эта функция у вас уже определена
                        place = place,
                        onDrive = {
                            mapViewRef?.let { 
                                buildRoute(it, destination, place.point) { status = "Маршрут построен до: ${place.title}" } 
                            }
                        },
                        onParked = {
                            status = "Парковка отмечена"
                        }
                    )
                }
                // ... остальной код (GPS Engine и т.д.)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onStop()
        }
    }
}
