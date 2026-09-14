package ru.mukapro.app

import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OsmParkingRepository {
    suspend fun getFreeParkingsInMoscow(): List<ParkingPlace> = withContext(Dispatchers.IO) {
        // Запрос к Overpass API: ищем все парковки в административных границах Москвы
        val query = """
            [out:json];
            area["name"="Москва"]["admin_level"="4"]->.moscow;
            (
              node["amenity"="parking"](area.moscow);
              way["amenity"="parking"](area.moscow);
            );
            out center qt;
        """.trimIndent()
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://overpass-api.de/api/interpreter?data=$encodedQuery")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.setRequestProperty("User-Agent", "MukaproAndroidApp/1.0")
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        
        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            val elements = jsonObject.getJSONArray("elements")
            
            val parkings = mutableListOf<ParkingPlace>()
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: continue
                
                // Пропускаем явно платные парковки
                if (tags.optString("fee", "no") == "yes") continue
                
                val name = tags.optString("name", "Бесплатная парковка")
                val access = tags.optString("access", "public")
                
                // Для node координаты в корне, для way (полигонов) берем центр
                val lat = if (el.has("lat")) el.getDouble("lat") else el.getJSONObject("center").getDouble("lat")
                val lon = if (el.has("lon")) el.getDouble("lon") else el.getJSONObject("center").getDouble("lon")
                
                parkings.add(
                    ParkingPlace(
                        id = el.getString("id"),
                        title = name,
                        type = "FREE",
                        distance = "—", // Расстояние можно рассчитать позже по координатам
                        walk = "—",
                        probability = 80,
                        score = 85,
                        price = "0 ₽",
                        access = if (access == "private") "Шлагбаум" else "Свободный",
                        point = Point(lat, lon)
                    )
                )
            }
            // Ограничиваем выдачу для стабильности MVP (например, первые 100 ближайших или случайных)
            parkings.take(100)
        } finally {
            connection.disconnect()
        }
    }
}
