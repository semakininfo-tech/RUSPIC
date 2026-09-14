package ru.mukapro.app

import android.app.Application
import org.osmdroid.config.Configuration

class MukaproApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализация osmdroid вместо Yandex
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = cacheDir
        }
    }
}
