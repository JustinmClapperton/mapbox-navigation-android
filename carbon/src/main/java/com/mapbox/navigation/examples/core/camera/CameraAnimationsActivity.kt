package com.mapbox.navigation.examples.core.camera

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraChange
import com.mapbox.maps.CameraChangeMode
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapLoadError
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.Style.Companion.MAPBOX_STREETS
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.CircleLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.plugin.animation.getCameraAnimationsPlugin
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadErrorListener
import com.mapbox.maps.plugin.gestures.GesturesPluginImpl
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.getGesturesPlugin
import com.mapbox.maps.plugin.location.LocationComponentActivationOptions
import com.mapbox.maps.plugin.location.LocationComponentPlugin
import com.mapbox.maps.plugin.location.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.location.modes.RenderMode
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigation.Companion.defaultNavigationOptionsBuilder
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.MapMatcherResult
import com.mapbox.navigation.core.trip.session.MapMatcherResultObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.examples.core.R
import com.mapbox.navigation.examples.core.camera.AnimationAdapter.OnAnimationButtonClicked
import com.mapbox.navigation.examples.util.ThemeUtil
import com.mapbox.navigation.ui.base.internal.route.RouteConstants
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSourceOptions
import com.mapbox.navigation.ui.maps.internal.route.arrow.MapboxRouteArrowAPI
import com.mapbox.navigation.ui.maps.internal.route.arrow.MapboxRouteArrowActions
import com.mapbox.navigation.ui.maps.internal.route.arrow.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineAPI
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineActions
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineResourceProviderFactory.getRouteLineResourceProvider
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.RouteArrowLayerInitializer
import com.mapbox.navigation.ui.maps.route.RouteLineLayerInitializer
import com.mapbox.navigation.ui.maps.route.arrow.api.RouteArrowAPI
import com.mapbox.navigation.ui.maps.route.line.api.RouteLineAPI
import com.mapbox.turf.TurfMeasurement
import kotlinx.android.synthetic.main.layout_camera_animations.*
import timber.log.Timber

class CameraAnimationsActivity :
    AppCompatActivity(),
    PermissionsListener,
    OnAnimationButtonClicked,
    OnMapLongClickListener {

    private val permissionsManager = PermissionsManager(this)
    private var locationComponent: LocationComponentPlugin? = null
    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapboxNavigation: MapboxNavigation
    private val replayRouteMapper = ReplayRouteMapper()
    private val mapboxReplayer = MapboxReplayer()

    private var routeLineAPI: RouteLineAPI? = null
    private var routeArrowAPI: RouteArrowAPI? = null
    private val routeLineView = MapboxRouteLineView()
    private val routeArrowView = MapboxRouteArrowView()
    private var routeLineLayerInitializer: RouteLineLayerInitializer? = null
    private var routeArrowLayerInitializer: RouteArrowLayerInitializer? = null

    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val overviewEdgeInsets: EdgeInsets by lazy {
        EdgeInsets(
            10.0 * pixelDensity,
            10.0 * pixelDensity,
            10.0 * pixelDensity,
            10.0 * pixelDensity
        )
    }
    private val followingEdgeInsets: EdgeInsets by lazy {
        EdgeInsets(
            mapboxMap.getSize().height.toDouble() * 2.0 / 3.0,
            0.0 * pixelDensity,
            0.0 * pixelDensity,
            0.0 * pixelDensity
        )
    }

    private var lookAtPoint: Point? = null
        set(value) {
            field = value
            if (value != null) {
                poiSource.geometry(value)
            } else {
                poiSource.featureCollection(FeatureCollection.fromFeatures(emptyList()))
            }
        }
    private val poiLayer = CircleLayer("circle_layer", "circle_source")
        .circleColor(Color.RED)
        .circleRadius(10.0)
    private val poiSource = GeoJsonSource(
        GeoJsonSource.Builder("circle_source").data("")
    )

    private val mapMatcherResultObserver = object : MapMatcherResultObserver {
        override fun onNewMapMatcherResult(mapMatcherResult: MapMatcherResult) {
            if (mapMatcherResult.keyPoints.isEmpty()) {
                locationComponent?.forceLocationUpdate(mapMatcherResult.enhancedLocation)
            } else {
                locationComponent?.forceLocationUpdate(mapMatcherResult.keyPoints, false)
            }
            viewportDataSource.onLocationChanged(mapMatcherResult.enhancedLocation)

            lookAtPoint?.run {
                val point = Point.fromLngLat(
                    mapMatcherResult.enhancedLocation.longitude,
                    mapMatcherResult.enhancedLocation.latitude,
                )
                val bearing = TurfMeasurement.bearing(point, this)
                viewportDataSource.followingBearingPropertyOverride(bearing)
            }

            viewportDataSource.evaluate()
            if (mapMatcherResult.isTeleport) {
                navigationCamera.resetFrame()
            }
        }
    }

    private val routeProgressObserver = object : RouteProgressObserver {
        override fun onRouteProgressChanged(routeProgress: RouteProgress) {
            viewportDataSource.onRouteProgressChanged(routeProgress)
            viewportDataSource.evaluate()

            routeLineAPI!!.updateUpcomingRoutePointIndex(routeProgress)
            routeLineAPI!!.updateVanishingPointState(routeProgress.currentState)
            routeArrowAPI!!.addUpComingManeuverArrow(routeProgress)
        }
    }

    private val routesObserver = object : RoutesObserver {
        override fun onRoutesChanged(routes: List<DirectionsRoute>) {
            if (routes.isNotEmpty()) {
                routeLineAPI!!.setRoutes(listOf(routes[0]))
                startSimulation(routes[0])
                viewportDataSource.onRouteChanged(routes.first())
                viewportDataSource.overviewPaddingPropertyOverride(overviewEdgeInsets)
                viewportDataSource.evaluate()
                navigationCamera.requestNavigationCameraToOverview()
            } else {
                navigationCamera.requestNavigationCameraToIdle()
            }
        }
    }

    private val onIndicatorPositionChangedListener = object : OnIndicatorPositionChangedListener {
        override fun onIndicatorPositionChanged(point: Point) {
            routeLineAPI!!.updateTraveledRouteLine(point)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_camera_animations)
        mapboxMap = mapView.getMapboxMap()

        initNavigation()

        viewportDataSource = MapboxNavigationViewportDataSource(
            MapboxNavigationViewportDataSourceOptions.Builder().build(),
            mapboxMap
        )
        navigationCamera = NavigationCamera(
            mapView.getMapboxMap(),
            mapView.getCameraAnimationsPlugin(),
            mapView.getGesturesPlugin(),
            viewportDataSource
        )

        routeLineLayerInitializer = RouteLineLayerInitializer.Builder(this).build()
        routeArrowLayerInitializer = RouteArrowLayerInitializer.Builder(this)
            // todo workaround, arrows currently do not perform any out-of-the-box z-ordering
            //  and route line doesn't expose layer ID getter
            .withAboveLayerId(RouteConstants.PRIMARY_ROUTE_TRAFFIC_LAYER_ID)
            .build()

        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            init()
        } else {
            permissionsManager.requestLocationPermissions(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun init() {
        initRouteLine()
        initAnimations()
        initStyle()
        initCameraListeners()
        initButtons()
        mapboxNavigation.startTripSession()
    }

    private fun initRouteLine() {
        val routeStyleRes = ThemeUtil.retrieveAttrResourceId(
            this,
            R.attr.navigationViewRouteStyle,
            R.style.MapboxStyleNavigationMapRoute
        )
        val resourceProvider = getRouteLineResourceProvider(this, routeStyleRes)
        routeLineAPI = MapboxRouteLineAPI(MapboxRouteLineActions(resourceProvider), routeLineView)
        routeArrowAPI = MapboxRouteArrowAPI(MapboxRouteArrowActions(), routeArrowView)
    }

    private fun initButtons() {
        gravitate_left.setOnClickListener {
            val currentPadding = mapboxMap.getCameraOptions(null).padding!!
            val padding = EdgeInsets(
                currentPadding.top,
                0.0,
                currentPadding.bottom,
                120.0 * pixelDensity
            )
            viewportDataSource.followingPaddingPropertyOverride(padding)
            viewportDataSource.evaluate()
        }

        gravitate_right.setOnClickListener {
            val currentPadding = mapboxMap.getCameraOptions(null).padding!!
            val padding = EdgeInsets(
                currentPadding.top,
                120.0 * pixelDensity,
                currentPadding.bottom,
                0.0
            )
            viewportDataSource.followingPaddingPropertyOverride(padding)
            viewportDataSource.evaluate()
        }

        gravitate_top.setOnClickListener {
            val currentPadding = mapboxMap.getCameraOptions(null).padding!!
            val padding = EdgeInsets(
                0.0,
                currentPadding.left,
                120.0 * pixelDensity,
                currentPadding.right
            )
            viewportDataSource.followingPaddingPropertyOverride(padding)
            viewportDataSource.evaluate()
        }

        gravitate_bottom.setOnClickListener {
            val currentPadding = mapboxMap.getCameraOptions(null).padding!!
            val padding = EdgeInsets(
                120.0 * pixelDensity,
                currentPadding.left,
                0.0,
                currentPadding.right
            )
            viewportDataSource.followingPaddingPropertyOverride(padding)
            viewportDataSource.evaluate()
        }
    }

    private fun initCameraListeners() {
        mapboxMap.addOnCameraChangeListener(
            object : OnCameraChangeListener {
                override fun onCameraChange(changeEvent: CameraChange, mode: CameraChangeMode) {
                    updateCameraChangeView()
                }
            }
        )
    }

    private fun initNavigation() {
        val navigationOptions = defaultNavigationOptionsBuilder(
            this,
            getMapboxAccessTokenFromResources()
        )
            .locationEngine(ReplayLocationEngine(mapboxReplayer))
            .build()
        mapboxNavigation = MapboxNavigation(navigationOptions).apply {
            registerLocationObserver(
                object : LocationObserver {

                    override fun onRawLocationChanged(rawLocation: Location) {
                        navigationCamera.requestNavigationCameraToIdle()
                        val point = Point.fromLngLat(rawLocation.longitude, rawLocation.latitude)
                        val cameraOptions = CameraOptions.Builder()
                            .center(point)
                            .zoom(13.0)
                            .build()
                        mapboxMap.jumpTo(cameraOptions)
                        locationComponent?.forceLocationUpdate(rawLocation)
                        mapboxNavigation.unregisterLocationObserver(this)
                    }

                    override fun onEnhancedLocationChanged(
                        enhancedLocation: Location,
                        keyPoints: List<Location>
                    ) {
                        // no impl
                    }
                }
            )
            registerRouteProgressObserver(routeProgressObserver)
            registerRoutesObserver(routesObserver)
            registerMapMatcherResultObserver(mapMatcherResultObserver)
        }

        mapboxReplayer.pushRealLocation(this, 0.0)
        mapboxReplayer.playbackSpeed(1.0)
        mapboxReplayer.play()
    }

    private fun startSimulation(route: DirectionsRoute) {
        mapboxReplayer.stop()
        mapboxReplayer.clearEvents()
        mapboxReplayer.pushRealLocation(this, 0.0)
        val replayEvents = replayRouteMapper.mapDirectionsRouteGeometry(route)
        mapboxReplayer.pushEvents(replayEvents)
        mapboxReplayer.seekTo(replayEvents.first())
        mapboxReplayer.play()
    }

    private fun initStyle() {
        mapboxMap.loadStyleUri(
            MAPBOX_STREETS,
            object : Style.OnStyleLoaded {
                override fun onStyleLoaded(style: Style) {
                    initializeLocationComponent(style)
                    getGesturesPlugin()?.addOnMapLongClickListener(
                        this@CameraAnimationsActivity
                    )
                    style.addSource(poiSource)
                    style.addLayer(poiLayer)

                    routeLineLayerInitializer!!.initializeLayers(style)
                    routeArrowLayerInitializer!!.initializeLayers(style)
                    routeLineAPI!!.updateViewStyle(style)
                    routeArrowAPI!!.updateViewStyle(style)
                }
            },
            object : OnMapLoadErrorListener {
                override fun onMapLoadError(mapViewLoadError: MapLoadError, msg: String) {
                    Timber.e("Error loading map: %s", mapViewLoadError.name)
                }
            }
        )
    }

    private fun initAnimations() {
        val adapter = AnimationAdapter(this, this)
        val manager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        animationsList.layoutManager = manager
        animationsList.adapter = adapter
    }

    @SuppressLint("SetTextI18n")
    private fun updateCameraChangeView() {
        mapboxMap.getCameraOptions(null).let { currentMapCamera ->
            cameraChangeView_state.text = "state: ${navigationCamera.state}"
            cameraChangeView_lng.text = "lng: " +
                currentMapCamera.center?.longitude().formatNumber()
            cameraChangeView_lat.text = "lat: ${currentMapCamera.center?.latitude().formatNumber()}"
            cameraChangeView_zoom.text = "zoom: ${currentMapCamera.zoom.formatNumber()}"
            cameraChangeView_bearing.text = "bearing: ${currentMapCamera.bearing.formatNumber()}"
            cameraChangeView_pitch.text = "pitch: ${currentMapCamera.pitch.formatNumber()}"
            cameraChangeView_padding.text =
                """
                    |padding:
                    |  top: ${currentMapCamera.padding?.top.formatNumber()}
                    |  left: ${currentMapCamera.padding?.left.formatNumber()}
                    |  bottom: ${currentMapCamera.padding?.bottom.formatNumber()}
                    |  right: ${currentMapCamera.padding?.right.formatNumber()}
               """.trimMargin()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onButtonClicked(animationType: AnimationType) {
        when (animationType) {
            AnimationType.Following -> {
                viewportDataSource.followingPaddingPropertyOverride(followingEdgeInsets)
                viewportDataSource.evaluate()
                navigationCamera.requestNavigationCameraToFollowing()
            }
            AnimationType.Overview -> {
                viewportDataSource.overviewPaddingPropertyOverride(overviewEdgeInsets)
                viewportDataSource.evaluate()
                navigationCamera.requestNavigationCameraToOverview()
            }
            AnimationType.ToPOI -> {
                val lastLocation = locationComponent!!.lastKnownLocation!!
                val center = Point.fromLngLat(
                    lastLocation.longitude + 0.0123,
                    lastLocation.latitude + 0.0123
                )
                navigationCamera.requestNavigationCameraToIdle()
                mapView.getCameraAnimationsPlugin().flyTo(
                    CameraOptions.Builder()
                        .center(center)
                        .bearing(0.0)
                        .zoom(14.0)
                        .pitch(0.0)
                        .build(),
                    1500
                )
            }
            AnimationType.LookAtPOIWhenFollowing -> {
                if (lookAtPoint == null) {
                    val center = mapboxMap.getCameraOptions(null).center
                    lookAtPoint = center.let {
                        Point.fromLngLat(
                            (it?.longitude() ?: 0.0) + 0.003,
                            (it?.latitude() ?: 0.0) + 0.003
                        )
                    }?.also {
                        viewportDataSource.additionalPointsToFrameForFollowing(listOf(it))
                        viewportDataSource.followingBearingPropertyOverride(
                            TurfMeasurement.bearing(center!!, it)
                        )
                        viewportDataSource.evaluate()
                    }
                } else {
                    lookAtPoint = null
                    viewportDataSource.additionalPointsToFrameForFollowing(emptyList())
                    viewportDataSource.followingBearingPropertyOverride(null)
                    viewportDataSource.evaluate()
                }
            }
        }
    }

    private fun findRoute(origin: Point, destination: Point) {
        val routeOptions: RouteOptions = RouteOptions.builder()
            .applyDefaultParams()
            .accessToken(getMapboxAccessTokenFromResources())
            .coordinates(listOf(origin, destination))
            .alternatives(true)
            .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .annotationsList(
                listOf(
                    DirectionsCriteria.ANNOTATION_SPEED,
                    DirectionsCriteria.ANNOTATION_DISTANCE,
                    DirectionsCriteria.ANNOTATION_CONGESTION
                )
            )
            .build()

        mapboxNavigation.requestRoutes(routeOptions)
    }

    override fun onMapLongClick(point: Point): Boolean {
        locationComponent?.let { locComp ->
            val currentLocation = locComp.lastKnownLocation
            if (currentLocation != null) {
                val originPoint = Point.fromLngLat(
                    currentLocation.longitude,
                    currentLocation.latitude
                )
                findRoute(originPoint, point)
            }
        }
        return false
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        navigationCamera.resetFrame()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        mapboxNavigation.onDestroy()
    }

    private fun initializeLocationComponent(style: Style) {
        locationComponent = getLocationComponent()
        val activationOptions = LocationComponentActivationOptions.builder(this, style)
            .useDefaultLocationEngine(false) // SBNOTE: I think this should be false eventually
            .build()
        locationComponent?.let {
            it.activateLocationComponent(activationOptions)
            it.enabled = true
            it.renderMode = RenderMode.GPS
            it.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        }
    }

    private fun getMapboxAccessTokenFromResources(): String {
        return getString(this.resources.getIdentifier("mapbox_access_token", "string", packageName))
    }

    private fun getLocationComponent(): LocationComponentPlugin? {
        return mapView.getPlugin(LocationComponentPlugin::class.java)
    }

    private fun getGesturesPlugin(): GesturesPluginImpl? {
        return mapView.getPlugin(GesturesPluginImpl::class.java)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(
            this,
            "This app needs location and storage permissions in order to show its functionality.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            init()
        } else {
            Toast.makeText(
                this,
                "You didn't grant location permissions.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun Number?.formatNumber() = "%.8f".format(this)
}
