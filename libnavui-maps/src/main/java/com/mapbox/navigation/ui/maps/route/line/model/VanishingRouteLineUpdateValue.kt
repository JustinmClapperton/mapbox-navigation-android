package com.mapbox.navigation.ui.maps.route.line.model

import com.mapbox.maps.extension.style.expressions.generated.Expression

/**
 * Represents data for updating the appearance of the route line.
 *
 * @param trafficLineExpression the expression for the primary route traffic line
 * @param routeLineExpression the expression for the primary route line
 * @param casingLineExpression the expression for the primary route casing line
 */
class VanishingRouteLineUpdateValue internal constructor(
    private val trafficLineExpression: Expression,
    private val routeLineExpression: Expression,
    private val casingLineExpression: Expression
) {
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
}
