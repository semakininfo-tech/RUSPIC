package ru.mukapro.app

import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NominatimGeocoder {
    suspend fun searchAddress(query: String): List<com.yandex.mapkit.geometry.Point> = withContext(Dispatchers.IO) {
        // Оставляем старую функцию для совместимости
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&countrycodes=ru")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MukaproAndroidApp/1.0")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val points = mutableListOf<com.yandex.mapkit.geometry.Point>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                points.add(com.yandex.mapkit.geometry.Point(obj.getDouble("lat"), obj.getDouble("lon")))
            }
            points
        } finally {
            connection.disconnect()
        }
    }
    
    suspend fun searchAddressGeoPoint(query: String): List<GeoPoint> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&countrycodes=ru")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MukaproAndroidApp/1.0")
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val points = mutableListOf<GeoPoint>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                points.add(GeoPoint(obj.getDouble("lat"), obj.getDouble("lon")))
            }
            points
        } finally {
            connection.disconnect()
        }
    }
}
