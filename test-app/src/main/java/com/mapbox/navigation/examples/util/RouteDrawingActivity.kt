package com.mapbox.navigation.examples.util

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapLoadError
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMapOptions
import com.mapbox.maps.ResourceOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.CameraAnimationsPlugin
import com.mapbox.maps.plugin.animation.getCameraAnimationsPlugin
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadErrorListener
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.getLocationComponentPlugin
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.examples.core.R
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineUtils
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources

class RouteDrawingActivity: AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var navigationLocationProvider: NavigationLocationProvider
    private lateinit var locationComponent: LocationComponentPlugin
    private lateinit var mapCamera: CameraAnimationsPlugin
    private lateinit var routeDrawingUtil: RouteDrawingUtil
    private var routeDrawingUtilEnabled = false

    private val routeLineResources: RouteLineResources by lazy {
        RouteLineResources.Builder().build()
    }

    private val routeLineApi: MapboxRouteLineApi by lazy {
        MapboxRouteLineApi(options)
    }

    private val routeLineView by lazy {
        MapboxRouteLineView(options)
    }

    private val options: MapboxRouteLineOptions by lazy {
        MapboxRouteLineOptions.Builder(this)
            .withRouteLineResources(routeLineResources)
            .withRouteLineBelowLayerId("road-label")
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_route_drawing_activity)
        val mapboxMapOptions = MapboxMapOptions(this, resources.displayMetrics.density, null)
        val resourceOptions = ResourceOptions.Builder()
            .accessToken(getMapboxAccessTokenFromResources())
            .assetPath(filesDir.absolutePath)
            .cachePath(filesDir.absolutePath + "/mbx.db")
            .cacheSize(100000000L) // 100 MB
            .tileStorePath(filesDir.absolutePath + "/maps_tile_store/")
            .build()
        mapboxMapOptions.resourceOptions = resourceOptions
        mapView = MapView(this, mapboxMapOptions)
        val mapLayout = findViewById<RelativeLayout>(R.id.mapView_container)
        mapLayout.addView(mapView)
        navigationLocationProvider = NavigationLocationProvider()
        locationComponent = mapView.getLocationComponentPlugin().apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
        mapCamera = getMapCamera()

        init()
    }

    private fun init() {
        initStyle()
        initLocation()
        initListeners()
    }

    private fun initListeners() {
        findViewById<Button>(R.id.btnEnableLongPress).setOnClickListener {
            when (routeDrawingUtilEnabled) {
                false -> {
                    routeDrawingUtilEnabled != routeDrawingUtilEnabled
                    routeDrawingUtil.enable()
                    (it as Button).text = "Disable Long Press Map"
                }
                true -> {
                    routeDrawingUtilEnabled != routeDrawingUtilEnabled
                    routeDrawingUtil.disable()
                    (it as Button).text = "Enable Long Press Map"
                }
            }
        }

        findViewById<Button>(R.id.btnFetchRoute).setOnClickListener {
            routeDrawingUtil.fetchRoute(routeRequestCallback)
        }

        findViewById<Button>(R.id.btnRemoveLastPoint).setOnClickListener {
            routeDrawingUtil.removeLastPoint()
        }

        findViewById<Button>(R.id.btnClearPoints).setOnClickListener {
            routeDrawingUtil.clear()
            routeLineApi.clearRouteLine().let {
                routeLineView.render(mapView.getMapboxMap().getStyle()!!, it)
            }
        }
    }

    private val routeRequestCallback: RoutesRequestCallback = object: RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            //val routeLines = routes.map { com.mapbox.navigation.ui.maps.route.line.model.RouteLine(it, null) }

            // fixme temporary
            val route = DirectionsRoute.fromJson(getStaticRoute())
            val routeLines = listOf(com.mapbox.navigation.ui.maps.route.line.model.RouteLine(route, null))
            //


            routeLineApi.setRoutes(routeLines).let {
                routeLineView.render(mapView.getMapboxMap().getStyle()!!, it)
                routeDrawingUtil.clear()
            }

            // fixme temporary
            //routeDrawingUtil.addPoint(Point.fromLngLat(-122.526159, 37.971947))
            //routeDrawingUtil.addPoint(Point.fromLngLat(-122.52645, -122.52645))

            val restrictedSections = MapboxRouteLineUtils.getRestrictedRouteSections(route)
            val restrictedSectionsFeatures = restrictedSections.map { Feature.fromGeometry(LineString.fromLngLats(it)) }

            mapView.getMapboxMap().getStyle()?.getSourceAs<GeoJsonSource>(RouteDrawingUtil.LINE_LAYER_SOURCE_ID)?.featureCollection(
                FeatureCollection.fromFeatures(restrictedSectionsFeatures)
            )

            // restrictedSections[0].forEach {
            //     routeDrawingUtil.addPoint(it)
            // }
            // restrictedSections[1].forEach {
            //     routeDrawingUtil.addPoint(it)
            // }



        }

        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
            Toast.makeText(this@RouteDrawingActivity, throwable.message, Toast.LENGTH_SHORT).show()
        }

        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
            Toast.makeText(this@RouteDrawingActivity, "Fetch Route Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initLocation() {
        val location = Location("").also {
            it.latitude = 37.975391
            it.longitude = -122.523667
        }

        val point = Point.fromLngLat(-122.523667,37.975391)
        val cameraOptions = CameraOptions.Builder().center(point).zoom(14.0).build()
        mapView.getMapboxMap().jumpTo(cameraOptions)
        navigationLocationProvider.changePosition(
            location,
            listOf(),
            null,
            null
        )



        // LocationEngineProvider.getBestLocationEngine(this)
        //     .getLastLocation(
        //         object : LocationEngineCallback<LocationEngineResult> {
        //             override fun onSuccess(result: LocationEngineResult?) {
        //                 result?.lastLocation?.let { location ->
        //                     val point = Point.fromLngLat(location.longitude, location.latitude)
        //                     val cameraOptions = CameraOptions.Builder().center(point).zoom(14.0).build()
        //                     mapView.getMapboxMap().jumpTo(cameraOptions)
        //                     navigationLocationProvider.changePosition(
        //                         location,
        //                         listOf(),
        //                         null,
        //                         null
        //                     )
        //                 }
        //             }
        //
        //             override fun onFailure(exception: Exception) {
        //                 // Intentionally empty
        //             }
        //         }
        //     )
    }

    @SuppressLint("MissingPermission")
    private fun initStyle() {
        mapView.getMapboxMap().loadStyleUri(
            Style.MAPBOX_STREETS,
            {
                routeDrawingUtil = RouteDrawingUtil(mapView)
            },
            object : OnMapLoadErrorListener {
                @SuppressLint("LogNotTimber")
                override fun onMapLoadError(mapLoadError: MapLoadError, msg: String) {
                    Log.e(
                        RouteDrawingActivity::class.java.simpleName,
                        "Error loading map: " + mapLoadError.name
                    )
                }
            }
        )
    }

    private fun getMapboxAccessTokenFromResources(): String {
        return getString(this.resources.getIdentifier("mapbox_access_token", "string", packageName))
    }

    private fun getMapCamera(): CameraAnimationsPlugin {
        return mapView.getCameraAnimationsPlugin()
    }

    private fun getStaticRoute(): String {
        return "{\"routeIndex\":\"0\",\"distance\":1286.745,\"duration\":252.592,\"duration_typical\":245.6,\"geometry\":\"}tylgAd`guhFhKzBuD`g@iBdVa@nFtThE`RnDpFhBf^nF`C^|VvDQbCiD~c@`o@tC~EVzRj@jOfA~Rp@iAdQiBh^o@|N[fSSrE]nHmBvb@_G`oA}Bxg@qBpc@aA`UiAjWI|A]zH}@tSjUdBtM`ArGf@vMbAxAsYR}DVsEf@qK\",\"weight\":362.336,\"weight_name\":\"auto\",\"legs\":[{\"distance\":1286.745,\"duration\":252.592,\"duration_typical\":245.6,\"summary\":\"Lootens Place, 3rd Street\",\"admins\":[{\"iso_3166_1\":\"US\",\"iso_3166_1_alpha3\":\"USA\"}],\"steps\":[{\"distance\":22.991,\"duration\":4.138,\"duration_typical\":4.138,\"geometry\":\"}tylgAd`guhFhKzB\",\"name\":\"\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523667,37.975391],\"bearing_before\":0,\"bearing_after\":194,\"instruction\":\"Drive south.\",\"type\":\"depart\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":22.991,\"announcement\":\"Drive south. Then Turn right onto Laurel Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Drive south. Then Turn right onto Laurel Place.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":22.991,\"primary\":{\"text\":\"Laurel Place\",\"components\":[{\"text\":\"Laurel Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"},\"sub\":{\"text\":\"Nye Street\",\"components\":[{\"text\":\"Nye Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":4.656,\"intersections\":[{\"location\":[-122.523667,37.975391],\"bearings\":[194],\"entry\":[true],\"out\":0,\"geometry_index\":0,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"service\"}}]},{\"distance\":101,\"duration\":24.173,\"duration_typical\":24.173,\"geometry\":\"shylgA`dguhFuD`g@iBdVa@nF\",\"name\":\"Laurel Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.523729,37.975194],\"bearing_before\":194,\"bearing_after\":280,\"instruction\":\"Turn right onto Laurel Place.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":50,\"announcement\":\"Turn left onto Nye Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto Nye Street.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":101,\"primary\":{\"text\":\"Nye Street\",\"components\":[{\"text\":\"Nye Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":36.24,\"intersections\":[{\"location\":[-122.523729,37.975194],\"bearings\":[14,280],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":1,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.52437,37.975285],\"bearings\":[100,280],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":2,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.524741,37.975338],\"bearings\":[100,280],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":3,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":196,\"duration\":41.333,\"duration_typical\":41.333,\"geometry\":\"urylgAxjiuhFtThE`RnDpFhBf^nF`C^|VvD\",\"name\":\"Nye Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.524861,37.975355],\"bearing_before\":280,\"bearing_after\":193,\"instruction\":\"Turn left onto Nye Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":186,\"announcement\":\"In 600 feet, Turn right onto 5th Avenue.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 600 feet, Turn right onto 5th Avenue.</prosody></amazon:effect></speak>\"},{\"distanceAlongGeometry\":68.333,\"announcement\":\"Turn right onto 5th Avenue. Then, in 200 feet, Turn left onto Lootens Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto 5th Avenue. Then, in 200 feet, Turn left onto Lootens Place.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":196,\"primary\":{\"text\":\"5th Avenue\",\"components\":[{\"text\":\"5th Avenue\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"},\"sub\":{\"text\":\"Lootens Place\",\"components\":[{\"text\":\"Lootens Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":67.277,\"intersections\":[{\"location\":[-122.524861,37.975355],\"bearings\":[100,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":4,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.524962,37.975008],\"bearings\":[13,193],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":5,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.52505,37.974703],\"bearings\":[13,199],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":6,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.525103,37.974582],\"bearings\":[19,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":7,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.525223,37.974082],\"bearings\":[11,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":8,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.525239,37.974017],\"bearings\":[11,191],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":9,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":59,\"duration\":8.629,\"duration_typical\":8.629,\"geometry\":\"cgvlgAdhjuhFQbCiD~c@\",\"name\":\"5th Avenue\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.525331,37.973634],\"bearing_before\":191,\"bearing_after\":280,\"instruction\":\"Turn right onto 5th Avenue.\",\"type\":\"turn\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":50,\"announcement\":\"Turn left onto Lootens Place.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto Lootens Place.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":59,\"primary\":{\"text\":\"Lootens Place\",\"components\":[{\"text\":\"Lootens Place\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":16.944,\"intersections\":[{\"location\":[-122.525331,37.973634],\"bearings\":[11,280],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":10,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.525397,37.973643],\"bearings\":[100,280],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":11,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":199,\"duration\":56.764,\"duration_typical\":56.764,\"geometry\":\"_mvlgAhqkuhF`o@tC~EVzRj@jOfA~Rp@\",\"name\":\"Lootens Place\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.525989,37.973728],\"bearing_before\":280,\"bearing_after\":184,\"instruction\":\"Turn left onto Lootens Place.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":189,\"announcement\":\"In 700 feet, Turn right onto 3rd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In 700 feet, Turn right onto 3rd Street.</prosody></amazon:effect></speak>\"},{\"distanceAlongGeometry\":50,\"announcement\":\"Turn right onto 3rd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn right onto 3rd Street.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":199,\"primary\":{\"text\":\"3rd Street\",\"components\":[{\"text\":\"3rd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"right\"}}],\"driving_side\":\"right\",\"weight\":78.291,\"intersections\":[{\"location\":[-122.525989,37.973728],\"bearings\":[100,184],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":12,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.526064,37.972959],\"bearings\":[4,185],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":13,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.526098,37.972529],\"bearings\":[3,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":15,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}},{\"location\":[-122.526134,37.972267],\"bearings\":[6,184],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":16,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"street\"}}]},{\"distance\":529,\"duration\":68.78,\"duration_typical\":61.788,\"geometry\":\"u}rlgA|{kuhFiAdQiBh^o@|N[fSSrE]nHmBvb@_G`oA}Bxg@qBpc@aA`UiAjWI|A]zH}@tS\",\"name\":\"3rd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.526159,37.971947],\"bearing_before\":184,\"bearing_after\":279,\"instruction\":\"Turn right onto 3rd Street.\",\"type\":\"end of road\",\"modifier\":\"right\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":515.667,\"announcement\":\"In a quarter mile, Turn left onto D Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">In a quarter mile, Turn left onto D Street.</prosody></amazon:effect></speak>\"},{\"distanceAlongGeometry\":91.111,\"announcement\":\"Turn left onto D Street. Then, in 400 feet, Turn left onto 2nd Street.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto D Street. Then, in 400 feet, Turn left onto 2nd Street.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":529,\"primary\":{\"text\":\"D Street\",\"components\":[{\"text\":\"D Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"},\"sub\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":82.471,\"intersections\":[{\"location\":[-122.526159,37.971947],\"bearings\":[4,279],\"classes\":[\"restricted\",\"primary\"],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":17,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52645,-122.52645],\"bearings\":[99,278],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":18,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.526951,37.972037],\"bearings\":[98,277],\"classes\":[\"restricted\",\"primary\"],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":19,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.527206,37.972061],\"bearings\":[97,273],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":20,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52753,37.972075],\"bearings\":[93,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":21,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.527636,37.972085],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":22,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.527788,37.9721],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":23,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.52836,37.972155],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":24,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.529641,37.972283],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":25,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.530294,37.972346],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":26,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.530879,37.972403],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":27,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.531232,37.972436],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":28,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.531622,37.972473],\"bearings\":[97,278],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":29,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.531669,37.972478],\"bearings\":[98,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":30,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.531827,37.972493],\"bearings\":[97,277],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":31,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}}]},{\"distance\":107,\"duration\":33.907,\"duration_typical\":33.907,\"geometry\":\"watlgAzrwuhFjUdBtM`ArGf@vMbA\",\"name\":\"D Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.532158,37.972524],\"bearing_before\":277,\"bearing_after\":186,\"instruction\":\"Turn left onto D Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":94.444,\"announcement\":\"Turn left onto 2nd Street. Then, in 200 feet, You will arrive at your destination.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">Turn left onto 2nd Street. Then, in 200 feet, You will arrive at your destination.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":107,\"primary\":{\"text\":\"2nd Street\",\"components\":[{\"text\":\"2nd Street\",\"type\":\"text\"}],\"type\":\"turn\",\"modifier\":\"left\"}}],\"driving_side\":\"right\",\"weight\":45.159,\"intersections\":[{\"location\":[-122.532158,37.972524],\"bearings\":[97,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":32,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.532209,37.972166],\"bearings\":[6,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":33,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.532242,37.971931],\"bearings\":[6,187],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":34,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}},{\"location\":[-122.532262,37.971793],\"bearings\":[7,186],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":35,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"tertiary\"}}]},{\"distance\":72.754,\"duration\":14.868,\"duration_typical\":14.868,\"geometry\":\"ierlgAn{wuhFxAsYR}DVsEf@qK\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.532296,37.971557],\"bearing_before\":186,\"bearing_after\":98,\"instruction\":\"Turn left onto 2nd Street.\",\"type\":\"turn\",\"modifier\":\"left\"},\"voiceInstructions\":[{\"distanceAlongGeometry\":55.556,\"announcement\":\"You have arrived at your destination.\",\"ssmlAnnouncement\":\"<speak><amazon:effect name=\\\"drc\\\"><prosody rate=\\\"1.08\\\">You have arrived at your destination.</prosody></amazon:effect></speak>\"}],\"bannerInstructions\":[{\"distanceAlongGeometry\":72.754,\"primary\":{\"text\":\"You will arrive at your destination\",\"components\":[{\"text\":\"You will arrive at your destination\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"straight\"}},{\"distanceAlongGeometry\":55.556,\"primary\":{\"text\":\"You have arrived at your destination\",\"components\":[{\"text\":\"You have arrived at your destination\",\"type\":\"text\"}],\"type\":\"arrive\",\"modifier\":\"straight\"}}],\"driving_side\":\"right\",\"weight\":31.298,\"intersections\":[{\"location\":[-122.532296,37.971557],\"bearings\":[6,98],\"entry\":[false,true],\"in\":0,\"out\":1,\"geometry_index\":36,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.53187,37.971512],\"bearings\":[98,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":37,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.531775,37.971502],\"bearings\":[98,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":38,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}},{\"location\":[-122.531669,37.97149],\"bearings\":[97,278],\"entry\":[true,false],\"in\":1,\"out\":0,\"geometry_index\":39,\"is_urban\":true,\"admin_index\":0,\"mapbox_streets_v8\":{\"class\":\"primary\"}}]},{\"distance\":0,\"duration\":0,\"duration_typical\":0,\"geometry\":\"{_rlgAvgvuhF??\",\"name\":\"2nd Street\",\"mode\":\"driving\",\"maneuver\":{\"location\":[-122.531468,37.97147],\"bearing_before\":97,\"bearing_after\":0,\"instruction\":\"You have arrived at your destination.\",\"type\":\"arrive\"},\"voiceInstructions\":[],\"bannerInstructions\":[],\"driving_side\":\"right\",\"weight\":0,\"intersections\":[{\"location\":[-122.531468,37.97147],\"bearings\":[277],\"entry\":[true],\"in\":0,\"geometry_index\":40,\"admin_index\":0}]}],\"annotation\":{\"distance\":[22.6,57.2,33.1,10.7,39.6,34.8,14.3,56.6,7.4,43.4,5.9,52.8,85.9,12.5,35.5,29.3,35.7,25.9,44.4,22.5,28.5,9.4,13.4,50.6,113.3,57.7,51.7,31.2,34.5,4.2,14,29.3,40.1,26.3,15.5,26.4,37.7,8.4,9.4,17.8],\"congestion\":[\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"unknown\",\"unknown\",\"low\",\"moderate\",\"low\",\"low\",\"unknown\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"low\",\"unknown\",\"unknown\",\"unknown\",\"moderate\",\"low\",\"unknown\",\"unknown\",\"low\",\"low\",\"low\",\"low\"]}}],\"routeOptions\":{\"baseUrl\":\"https://api.mapbox.com/\",\"user\":\"mapbox\",\"profile\":\"driving-traffic\",\"coordinates\":[[-122.5237628,37.9754096],[-122.5314743,37.9714342]],\"alternatives\":true,\"language\":\"en\",\"bearings\":\"147.032028,45;\",\"continue_straight\":false,\"roundabout_exits\":false,\"geometries\":\"polyline6\",\"overview\":\"full\",\"steps\":true,\"annotations\":\"congestion,distance\",\"voice_instructions\":true,\"banner_instructions\":true,\"voice_units\":\"imperial\",\"access_token\":\"omitted\",\"uuid\":\"IpVPAi2-NVeMFql46rqadH7Ag5QrBGPWCRdfz-cUrXHk2e1n7gKjYg==\"},\"voiceLocale\":\"en-US\"}"
    }
}
