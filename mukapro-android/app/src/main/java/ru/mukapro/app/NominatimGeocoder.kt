package ru.mukapro.app

import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NominatimGeocoder {
    suspend fun searchAddress(query: String): List<Point> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // Ограничиваем поиск Россией (countrycodes=ru) для повышения точности
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&countrycodes=ru")
        val connection = url.openConnection() as HttpURLConnection
        
        // Nominatim требует уникальный User-Agent
        connection.setRequestProperty("User-Agent", "MukaproAndroidApp/1.0 (contact@yourdomain.com)")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val points = mutableListOf<Point>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val lat = obj.getDouble("lat")
                val lon = obj.getDouble("lon")
                points.add(Point(lat, lon))
            }
            points
        } finally {
            connection.disconnect()
        }
    }
}
