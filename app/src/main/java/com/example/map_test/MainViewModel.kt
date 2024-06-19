package com.example.map_test

import androidx.lifecycle.ViewModel
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: ViewModel() {

    private val _routePoints = MutableStateFlow<List<Point>>(emptyList())
    val routePoints = _routePoints.asStateFlow()

    private val _routes = MutableStateFlow<List<DrivingRoute>>(emptyList())
    val routes = _routes.asStateFlow()

    fun updateRoutePoints(points: List<Point>) {
        _routePoints.value = points
    }

    fun updateRoutes(routes: List<DrivingRoute>) {
        _routes.value = routes
    }
}