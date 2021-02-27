package com.mapbox.navigation.ui.maps.route.line.model

import com.mapbox.geojson.FeatureCollection

/**
 * Represents data used to remove the route line(s) from the map.
 *
 * @param primaryRouteSource a feature collection representing the primary route
 * @param altRoute1Source a feature collection representing an alternative route
 * @param altRoute2Source a feature collection representing an alternative route
 * @param waypointsSource a feature collection representing the origin and destination icons
 */
class RouteLineClearValue internal constructor(
    private val primaryRouteSource: FeatureCollection,
    private val altRoute1Source: FeatureCollection,
    private val altRoute2Source: FeatureCollection,
    private val waypointsSource: FeatureCollection
) {
    /**
     * @return the primary route feature collection
     */
    fun getPrimaryRouteSource(): FeatureCollection = primaryRouteSource
    /**
     * @return an alternative route feature collection
     */
    fun getAlternativeRoute1Source(): FeatureCollection = altRoute1Source
    /**
     * @return an alternative route feature collection
     */
    fun getAlternativeRoute2Source(): FeatureCollection = altRoute2Source
    /**
     * @return a feature collection for displaying the origin and destination points
     */
    fun getOriginAndDestinationPointsSource(): FeatureCollection = waypointsSource
}
