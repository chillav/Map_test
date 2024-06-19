package com.example.map_test

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class MapApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        MapKitFactory.setApiKey(MAPKIT_API_KEY)
    }

    companion object {
        const val MAPKIT_API_KEY = "" // add token here
    }
}