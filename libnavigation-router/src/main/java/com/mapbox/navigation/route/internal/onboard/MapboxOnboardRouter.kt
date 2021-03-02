package com.mapbox.navigation.route.internal.onboard

import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.base.common.logger.Logger
import com.mapbox.base.common.logger.model.Message
import com.mapbox.base.common.logger.model.Tag
import com.mapbox.navigation.base.internal.route.RouteUrl
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.RouteRefreshCallback
import com.mapbox.navigation.base.route.RouteRefreshError
import com.mapbox.navigation.base.route.Router
import com.mapbox.navigation.navigator.internal.MapboxNativeNavigator
import com.mapbox.navigation.route.onboard.OfflineRoute
import com.mapbox.navigation.utils.NavigationException
import com.mapbox.navigation.utils.internal.RequestMap
import com.mapbox.navigation.utils.internal.ThreadController
import com.mapbox.navigator.RouterError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MapboxOnboardRouter provides offline route fetching
 *
 * It uses offline storage path to store and retrieve data, setup endpoint,
 * tiles' version, token. Config is provided via [RoutingTilesOptions].
 *
 * @param navigatorNative Native Navigator
 * @param logger interface for logging any events
 */
class MapboxOnboardRouter(
    private val navigatorNative: MapboxNativeNavigator,
    private val logger: Logger
) : Router {

    private companion object {
        private val loggerTag = Tag("MapboxOnboardRouter")
    }

    private val mainJobControl by lazy { ThreadController.getMainScopeAndRootJob() }
    private val requests = RequestMap<Job>()

    /**
     * Fetch route based on [RouteOptions]
     *
     * @param routeOptions
     * @param callback Callback that gets notified with the results of the request
     */
    override fun getRoute(
        routeOptions: RouteOptions,
        callback: Router.Callback
    ): Int {
        val origin = routeOptions.coordinates().first()
        val destination = routeOptions.coordinates().last()
        val waypoints = routeOptions.coordinates().drop(1).dropLast(1)

        val offlineRouter = OfflineRoute.Builder(
            RouteUrl(
                accessToken = routeOptions.accessToken(),
                user = routeOptions.user(),
                profile = routeOptions.profile(),
                origin = origin,
                waypoints = waypoints,
                destination = destination,
                steps = routeOptions.steps()
                    ?: RouteUrl.STEPS_DEFAULT_VALUE,
                voiceInstruction = routeOptions.voiceInstructions()
                    ?: RouteUrl.VOICE_INSTRUCTION_DEFAULT_VALUE,
                voiceUnits = routeOptions.voiceUnits(),
                bannerInstruction = routeOptions.bannerInstructions()
                    ?: RouteUrl.BANNER_INSTRUCTION_DEFAULT_VALUE,
                roundaboutExits = routeOptions.roundaboutExits()
                    ?: RouteUrl.ROUNDABOUT_EXITS_DEFAULT_VALUE,
                alternatives = routeOptions.alternatives()
                    ?: RouteUrl.ALTERNATIVES_DEFAULT_VALUE,
                continueStraight = routeOptions.continueStraight(),
                exclude = routeOptions.exclude(),
                language = routeOptions.language(),
                bearings = routeOptions.bearings(),
                waypointNames = routeOptions.waypointNames(),
                waypointTargets = routeOptions.waypointTargets(),
                waypointIndices = routeOptions.waypointIndices(),
                approaches = routeOptions.approaches(),
                radiuses = routeOptions.radiuses(),
                walkingSpeed = routeOptions.walkingOptions()?.walkingSpeed(),
                walkwayBias = routeOptions.walkingOptions()?.walkwayBias(),
                alleyBias = routeOptions.walkingOptions()?.alleyBias()
            )
        ).build()

        val requestId = requests.generateNextRequestId()
        val internalCallback = object : Router.Callback {
            override fun onResponse(routes: List<DirectionsRoute>) {
                requests.remove(requestId)
                callback.onResponse(routes)
            }

            override fun onFailure(throwable: Throwable) {
                requests.remove(requestId)
                callback.onFailure(throwable)
            }

            override fun onCanceled() {
                requests.remove(requestId)
                callback.onCanceled()
            }
        }
        requests.put(requestId, retrieveRoute(offlineRouter.buildUrl(), internalCallback))
        return requestId
    }

    override fun cancelRouteRequest(requestId: Int) {
        val request = requests.remove(requestId)
        if (request != null) {
            request.cancel()
        } else {
            logger.w(
                loggerTag,
                Message("Trying to cancel non-existing route request with id '$requestId'")
            )
        }
    }

    /**
     * Interrupts a route-fetching request if one is in progress.
     */
    override fun cancelAll() {
        requests.removeAll().forEach {
            it.cancel()
        }
    }

    /**
     * Release used resources.
     */
    override fun shutdown() {
        cancelAll()
    }

    /**
     * Refresh the traffic annotations for a given [DirectionsRoute]
     *
     * @param route DirectionsRoute the direction route to refresh
     * @param legIndex Int the index of the current leg in the route
     * @param callback Callback that gets notified with the results of the request
     */
    override fun getRouteRefresh(
        route: DirectionsRoute,
        legIndex: Int,
        callback: RouteRefreshCallback
    ): Int {
        callback.onError(
            RouteRefreshError(
                "Route refresh is not available when offline."
            )
        )
        return -1
    }

    override fun cancelRouteRefreshRequest(requestId: Int) {
        // Do nothing
    }

    private fun retrieveRoute(url: String, callback: Router.Callback): Job {
        return mainJobControl.scope.launch {
            try {
                val routerResult = getRoute(url)
                if (routerResult.isValue) {
                    val routes: List<DirectionsRoute> = parseDirectionsRoutes(routerResult.value!!)
                    callback.onResponse(routes)
                } else {
                    callback
                        .onFailure(NavigationException(generateErrorMessage(routerResult.error!!)))
                }
            } catch (e: CancellationException) {
                callback.onCanceled()
            }
        }
    }

    internal suspend fun getRoute(url: String) = withContext(ThreadController.IODispatcher) {
        navigatorNative.getRoute(url)
    }

    private suspend fun parseDirectionsRoutes(json: String): List<DirectionsRoute> =
        withContext(ThreadController.IODispatcher) {
            DirectionsResponse.fromJson(json).routes()
        }

    private fun generateErrorMessage(error: RouterError): String {
        val errorMessage =
            "Error occurred fetching offline route: ${error.error} - Code: ${error.code}"
        logger.e(loggerTag, Message(errorMessage))
        return errorMessage
    }
}
