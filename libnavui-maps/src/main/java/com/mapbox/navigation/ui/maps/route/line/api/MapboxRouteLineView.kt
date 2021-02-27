package com.mapbox.navigation.ui.maps.route.line.api

import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.navigation.ui.base.internal.model.route.RouteConstants
import com.mapbox.navigation.ui.base.model.Expected
import com.mapbox.navigation.ui.base.model.route.RouteLayerConstants
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineUtils.getLayerVisibility
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineUtils.initializeLayers
import com.mapbox.navigation.ui.maps.route.line.model.LayerVisibilityUpdate
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineClearValue
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineError
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineNoUpdate
import com.mapbox.navigation.ui.maps.route.line.model.RouteSetValue
import com.mapbox.navigation.ui.maps.route.line.model.VanishingRouteLineUpdateValue

/**
 * Responsible for rendering side effects produced by the [MapboxRouteLineApi]. The [MapboxRouteLineApi]
 * class consumes route data from the Navigation SDK and produces the data necessary to
 * visualize one or more routes on the map. This class renders the data from the [MapboxRouteLineApi]
 * by calling the appropriate map related commands so that the map can have an appearance that is
 * consistent with the state of the navigation SDK and the application.
 *
 * @param options resource options used rendering the route line on the map
 */
class MapboxRouteLineView(var options: MapboxRouteLineOptions) {

    /**
     * Applies drawing related side effects.
     *
     * @param style a valid [Style] instance
     * @param routeDrawData a [Expected<RouteSetValue, RouteLineError>]
     */
    fun renderRouteDrawData(style: Style, routeDrawData: Expected<RouteSetValue, RouteLineError>) {
        initializeLayers(style, options)

        when (routeDrawData) {
            is Expected.Success -> {
                updateLineGradient(
                    style,
                    RouteLayerConstants.PRIMARY_ROUTE_TRAFFIC_LAYER_ID,
                    routeDrawData.value.getTrafficLineExpression()
                )
                updateLineGradient(
                    style,
                    RouteLayerConstants.PRIMARY_ROUTE_LAYER_ID,
                    routeDrawData.value.getRouteLineExpression()
                )
                updateLineGradient(
                    style,
                    RouteLayerConstants.PRIMARY_ROUTE_CASING_LAYER_ID,
                    routeDrawData.value.getCasingLineExpression()
                )
                updateSource(
                    style,
                    RouteConstants.PRIMARY_ROUTE_SOURCE_ID,
                    routeDrawData.value.getPrimaryRouteSource()
                )
                updateSource(
                    style,
                    RouteConstants.ALTERNATIVE_ROUTE1_SOURCE_ID,
                    routeDrawData.value.getAlternativeRoute1Source()
                )
                updateSource(
                    style,
                    RouteConstants.ALTERNATIVE_ROUTE2_SOURCE_ID,
                    routeDrawData.value.getAlternativeRoute2Source()
                )
                updateLineGradient(
                    style,
                    RouteLayerConstants.ALTERNATIVE_ROUTE1_TRAFFIC_LAYER_ID,
                    routeDrawData.value.getAlternativeRoute1TrafficExpression()
                )
                updateLineGradient(
                    style,
                    RouteLayerConstants.ALTERNATIVE_ROUTE2_TRAFFIC_LAYER_ID,
                    routeDrawData.value.getAlternativeRoute2TrafficExpression()
                )
                updateSource(
                    style,
                    RouteConstants.WAYPOINT_SOURCE_ID,
                    routeDrawData.value.getOriginAndDestinationPointsSource()
                )
            }
            is Expected.Failure -> { }
        }
    }

    /**
     * Applies side effects related to the vanishing route line feature.
     *
     * @param style an instance of the Style
     * @param update an instance of VanishingRouteLineUpdateState
     */
    fun renderVanishingRouteLineUpdateValue(
        style: Style,
        update: Expected<VanishingRouteLineUpdateValue, RouteLineNoUpdate>
    ) {
        when (update) {
            is Expected.Failure -> { }
            is Expected.Success -> {
                initializeLayers(style, options)

                updateLineGradient(
                    style,
                    RouteLayerConstants.PRIMARY_ROUTE_TRAFFIC_LAYER_ID,
                    update.value.getTrafficLineExpression()
                )
                updateLineGradient(
                    style,
                    RouteLayerConstants.PRIMARY_ROUTE_LAYER_ID,
                    update.value.getRouteLineExpression()
                )
                updateLineGradient(
                    style,
                    RouteLayerConstants.PRIMARY_ROUTE_CASING_LAYER_ID,
                    update.value.getCasingLineExpression()
                )
            }
        }
    }

    /**
     * Applies side effects related to clearing the route(s) from the map.
     *
     * @param style an instance of the Style
     * @param clearRouteLineValue an instance of ClearRouteLineState
     */
    fun renderClearRouteLineValue(
        style: Style,
        clearRouteLineValue: Expected<RouteLineClearValue, RouteLineError>
    ) {
        when (clearRouteLineValue) {
            is Expected.Failure -> { }
            is Expected.Success -> {
                initializeLayers(style, options)

                updateSource(
                    style,
                    RouteConstants.PRIMARY_ROUTE_SOURCE_ID,
                    clearRouteLineValue.value.getPrimaryRouteSource()
                )
                updateSource(
                    style,
                    RouteConstants.ALTERNATIVE_ROUTE1_SOURCE_ID,
                    clearRouteLineValue.value.getAlternativeRoute1Source()
                )
                updateSource(
                    style,
                    RouteConstants.ALTERNATIVE_ROUTE2_SOURCE_ID,
                    clearRouteLineValue.value.getAlternativeRoute2Source()
                )
                updateSource(
                    style,
                    RouteConstants.WAYPOINT_SOURCE_ID,
                    clearRouteLineValue.value.getOriginAndDestinationPointsSource()
                )
            }
        }
    }

    /**
     * Applies side effects related to updating the visibility of the route line(s)
     *
     * @param style an instance of the Style
     * @param update an instance of [LayerVisibilityUpdate]
     */
    fun renderVisibilityUpdate(
        style: Style,
        update: Expected<LayerVisibilityUpdate, RouteLineError>
    ) {
        when (update) {
            is Expected.Failure -> { }
            is Expected.Success -> {
                initializeLayers(style, options)

                update.value.getLayerVisibilityChanges().forEach {
                    updateLayerVisibility(style, it.first, it.second)
                }
            }
        }
    }

    /**
     * Returns the visibility of the primary route map layer.
     *
     * @param style an instance of the Style
     *
     * @return the visibility value returned by the map.
     */
    fun getPrimaryRouteVisibility(style: Style): Visibility? {
        return getLayerVisibility(style, RouteLayerConstants.PRIMARY_ROUTE_LAYER_ID)
    }

    /**
     * Returns the visibility of the alternative route(s) map layer.
     *
     * @param style an instance of the Style
     *
     * @return the visibility value returned by the map.
     */
    fun getAlternativeRoutesVisibility(style: Style): Visibility? {
        return getLayerVisibility(style, RouteLayerConstants.ALTERNATIVE_ROUTE1_LAYER_ID)
    }

    /**
     * Sets the layer containing the origin and destination icons to visible.
     *
     * @param style an instance of the Style
     */
    fun showOriginAndDestinationPoints(style: Style) {
        updateLayerVisibility(style, RouteLayerConstants.WAYPOINT_LAYER_ID, Visibility.VISIBLE)
    }

    /**
     * Sets the layer containing the origin and destination icons to not visible.
     *
     * @param style an instance of the Style
     */
    fun hideOriginAndDestinationPoints(style: Style) {
        updateLayerVisibility(style, RouteLayerConstants.WAYPOINT_LAYER_ID, Visibility.NONE)
    }

    private fun updateLayerVisibility(style: Style, layerId: String, visibility: Visibility) {
        if (style.isFullyLoaded()) {
            style.getLayer(layerId)?.visibility(visibility)
        }
    }

    private fun updateSource(style: Style, sourceId: String, featureCollection: FeatureCollection) {
        if (style.isFullyLoaded()) {
            style.getSource(sourceId)?.let {
                (it as GeoJsonSource).featureCollection(featureCollection)
            }
        }
    }

    private fun updateLineGradient(style: Style, layerId: String, expression: Expression) {
        if (style.isFullyLoaded()) {
            style.getLayer(layerId)?.let {
                (it as LineLayer).lineGradient(expression)
            }
        }
    }
}
