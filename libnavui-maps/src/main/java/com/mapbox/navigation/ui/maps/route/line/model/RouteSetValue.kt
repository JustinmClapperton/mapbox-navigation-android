package com.mapbox.navigation.ui.maps.route.line.model

import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.extension.style.expressions.generated.Expression

/**
 * Represents the side effects for drawing routes on a map.
 *
 * @param primaryRouteSource the feature collection for the primary route line
 * @param trafficLineExpression the expression for the primary route traffic line
 * @param routeLineExpression the expression for the primary route line
 * @param casingLineExpression the expression for the primary route casing line
 * @param altRoute1TrafficExpression the expression for an alternative route traffic line
 * @param altRoute2TrafficExpression the expression for an alternative route traffic line
 * @param altRoute1Source the feature collection for an alternative route line
 * @param altRoute2Source the feature collection for an alternative route line
 * @param waypointsSource the feature collection for the origin and destination icons
 */
class RouteSetValue internal constructor(
    private val primaryRouteSource: FeatureCollection,
    private val trafficLineExpression: Expression,
    private val routeLineExpression: Expression,
    private val casingLineExpression: Expression,
    private val altRoute1TrafficExpression: Expression,
    private val altRoute2TrafficExpression: Expression,
    private val altRoute1Source: FeatureCollection,
    private val altRoute2Source: FeatureCollection,
    private val waypointsSource: FeatureCollection
) {
    /**
     * @return the feature collection for the primary route
     */
    fun getPrimaryRouteSource(): FeatureCollection = primaryRouteSource

    /**
     * @return the expression for the primary route traffic line
     */
    fun getTrafficLineExpression(): Expression = trafficLineExpression

    /**
     * @return the expression for the primary route line
     */
    fun getRouteLineExpression(): Expression = routeLineExpression
    /**
     * @return the expression for the primary route casing line
     */
    fun getCasingLineExpression(): Expression = casingLineExpression

    /**
     * @return the expression for an alternative route line
     */
    fun getAlternativeRoute1TrafficExpression(): Expression = altRoute1TrafficExpression

    /**
     * @return the expression for an alternative route line
     */
    fun getAlternativeRoute2TrafficExpression(): Expression = altRoute2TrafficExpression

    /**
     * @return the feature collection for an alternative route line
     */
    fun getAlternativeRoute1Source(): FeatureCollection = altRoute1Source
    /**
     * @return the feature collection for an alternative route line
     */
    fun getAlternativeRoute2Source(): FeatureCollection = altRoute2Source

    /**
     * @return a feature collection for the origin and destination icons
     */
    fun getOriginAndDestinationPointsSource(): FeatureCollection = waypointsSource
}
