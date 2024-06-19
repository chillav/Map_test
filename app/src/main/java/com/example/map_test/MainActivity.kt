package com.example.map_test

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    private lateinit var mapView: MapView
    private lateinit var map: Map
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var drivingRouter: DrivingRouter
    private lateinit var placeMarksCollection: MapObjectCollection
    private lateinit var routesCollection: MapObjectCollection
    private var drivingSession: DrivingSession? = null

    private val drivingRouteListener = object : DrivingSession.DrivingRouteListener {
        override fun onDrivingRoutes(drivingRoutes: MutableList<DrivingRoute>) {
            viewModel.updateRoutes(drivingRoutes)
        }

        override fun onDrivingRoutesError(p0: com.yandex.runtime.Error) {
            onUnexpectedError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        MapKitFactory.initialize(this)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapview)
        map = mapView.mapWindow.map

        placeMarksCollection = map.mapObjects.addCollection()
        routesCollection = map.mapObjects.addCollection()

        drivingRouter = DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)

        requestLocationPermissionIfNeeded(
            onSuccess = { refreshCurrentPosition() }
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.routePoints.collect(::onRoutePointsUpdated)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.routes.collect(::onRoutesUpdated)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()) {
            val firstPermissionGranted = grantResults.getOrNull(0) == PERMISSION_GRANTED
            val secondPermissionGranted = grantResults.getOrNull(1) == PERMISSION_GRANTED

            if (firstPermissionGranted && secondPermissionGranted) {
                refreshCurrentPosition()
            } else {
                Toast.makeText(this, R.string.denied_location_permission_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()

        super.onStop()
    }

    private fun requestLocationPermissionIfNeeded(onSuccess: () -> Unit) {
        if (isLocationPermissionGranted()) {
            onSuccess()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun onRoutePointsUpdated(points: List<Point>) {
        placeMarksCollection.clear()

        if (points.isEmpty()) {
            drivingSession?.cancel()
            viewModel.updateRoutes(emptyList())
            return
        }

        if (points.size < 2) return

        val requestPoints = buildList {
            val stops = points
                .subList(1, points.size - 1)
                .map { RequestPoint(it, RequestPointType.VIAPOINT, null, null) }

            add(RequestPoint(points.first(), RequestPointType.WAYPOINT, null, null))
            addAll(stops)
            add(RequestPoint(points.last(), RequestPointType.WAYPOINT, null, null))
        }

        drivingSession = drivingRouter.requestRoutes(
            requestPoints,
            DrivingOptions(),
            VehicleOptions(),
            drivingRouteListener,
        )
    }

    @SuppressLint("MissingPermission")
    private fun refreshCurrentPosition() {
        if (isLocationPermissionGranted().not()) {
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val latitude = it.latitude
                    val longitude = it.longitude
                    val startPoint = Point(latitude, longitude)

                    viewModel.updateRoutePoints(listOf(startPoint, destinationPoint))
                } ?: run {
                    onUnexpectedError()
                }
            }
            .addOnFailureListener {
                onUnexpectedError()
            }
    }

    private fun isLocationPermissionGranted(): Boolean {
        val isFineLocationGranted = ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
        val isCoarseLocationGranted = ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED

        return isFineLocationGranted && isCoarseLocationGranted
    }

    private fun onUnexpectedError() {
        Toast.makeText(this, R.string.unexpected_error, Toast.LENGTH_SHORT).show()
    }

    private fun onRoutesUpdated(routes: List<DrivingRoute>) {
        routesCollection.clear()
        if (routes.isEmpty()) return

        routes.forEachIndexed { index, route ->
            routesCollection.addPolyline(route.geometry).apply {
                if (index == 0) styleMainRoute() else styleAlternativeRoute()
            }
        }
    }

    private fun PolylineMapObject.styleMainRoute() {
        zIndex = 10f
        setStrokeColor(Color.BLUE)
        strokeWidth = 5f
        outlineColor = Color.BLACK
        outlineWidth = 3f
    }

    private fun PolylineMapObject.styleAlternativeRoute() {
        zIndex = 5f
        setStrokeColor(Color.GRAY)
        strokeWidth = 4f
        outlineColor = Color.BLACK
        outlineWidth = 2f
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private val destinationPoint = Point(59.954093, 30.305770)
    }
}