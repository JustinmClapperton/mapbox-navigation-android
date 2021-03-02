package com.mapbox.navigation.route.internal.offboard

import android.content.Context
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directionsrefresh.v1.MapboxDirectionsRefresh
import com.mapbox.api.directionsrefresh.v1.models.DirectionsRefreshResponse
import com.mapbox.base.common.logger.Logger
import com.mapbox.base.common.logger.model.Message
import com.mapbox.base.common.logger.model.Tag
import com.mapbox.navigation.base.internal.accounts.UrlSkuTokenProvider
import com.mapbox.navigation.base.route.RouteRefreshCallback
import com.mapbox.navigation.base.route.RouteRefreshError
import com.mapbox.navigation.base.route.Router
import com.mapbox.navigation.route.offboard.RouteBuilderProvider
import com.mapbox.navigation.route.offboard.router.routeOptions
import com.mapbox.navigation.route.offboard.routerefresh.RouteRefreshCallbackMapper
import com.mapbox.navigation.utils.NavigationException
import com.mapbox.navigation.utils.internal.RequestMap
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * MapboxOffboardRouter provides online route-fetching
 *
 * @param accessToken mapboxAccessToken token
 * @param context application [Context]
 * @param urlSkuTokenProvider [UrlSkuTokenProvider]
 */
class MapboxOffboardRouter(
    private val accessToken: String,
    private val context: Context,
    private val urlSkuTokenProvider: UrlSkuTokenProvider,
    private val refreshEnabled: Boolean,
    private val logger: Logger
) : Router {

    private companion object {
        private val TAG = Tag("MapboxOffboardRouter")
        private const val ERROR_FETCHING_ROUTE = "Error fetching route"
    }

    private val directionRequests = RequestMap<MapboxDirections>()
    private val refreshRequests = RequestMap<MapboxDirectionsRefresh>()

    /**
     * Fetch routes based on [RouteOptions].
     *
     * @param routeOptions RouteOptions
     * @param callback Callback that gets notified with the results of the request
     *
     * @return request ID, can be used to cancel the request with [cancelAll]
     */
    override fun getRoute(
        routeOptions: RouteOptions,
        callback: Router.Callback
    ): Int {
        val mapboxDirections = RouteBuilderProvider
            .getBuilder(accessToken, context, urlSkuTokenProvider)
            .routeOptions(routeOptions, refreshEnabled)
            .build()
        val requestId = directionRequests.put(mapboxDirections)
        mapboxDirections.enqueueCall(
            object : Callback<DirectionsResponse> {
                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    directionRequests.remove(requestId)
                    val routes = response.body()?.routes()
                    when {
                        call.isCanceled -> callback.onCanceled()
                        response.isSuccessful && !routes.isNullOrEmpty() -> {
                            callback.onResponse(routes)
                        }
                        else -> callback.onFailure(NavigationException(ERROR_FETCHING_ROUTE))
                    }
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    directionRequests.remove(requestId)
                    if (call.isCanceled) {
                        callback.onCanceled()
                    } else {
                        callback.onFailure(t)
                    }
                }
            }
        )
        return requestId
    }

    /**
     * Cancels a specific route request.
     *
     * @see [getRoute]
     */
    override fun cancelRouteRequest(requestId: Int) {
        val request = directionRequests.remove(requestId)
        if (request != null) {
            request.cancelCall()
        } else {
            logger.w(
                TAG,
                Message("Trying to cancel non-existing route request with id '$requestId'")
            )
        }
    }

    /**
     * Interrupts a route-fetching request if one is in progress.
     */
    override fun cancelAll() {
        directionRequests.removeAll().forEach {
            it.cancelCall()
        }
        refreshRequests.removeAll().forEach {
            it.cancelCall()
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
        val mapboxDirectionsRefresh = RouteBuilderProvider.getRefreshBuilder()
            .accessToken(accessToken)
            .requestId(route.routeOptions()?.requestUuid())
            .routeIndex(route.routeIndex()?.toIntOrNull() ?: 0)
            .legIndex(legIndex)
            .interceptor {
                val httpUrl = it.request().url()
                val skuUrl = urlSkuTokenProvider.obtainUrlWithSkuToken(httpUrl.url())
                it.proceed(it.request().newBuilder().url(skuUrl).build())
            }
            .build()
        val requestId = refreshRequests.put(mapboxDirectionsRefresh)

        mapboxDirectionsRefresh.enqueueCall(object : Callback<DirectionsRefreshResponse> {
            override fun onResponse(
                call: Call<DirectionsRefreshResponse>,
                response: Response<DirectionsRefreshResponse>
            ) {
                val routeAnnotations = response.body()?.route()
                var errorThrowable: Throwable? = null
                val refreshedDirectionsRoute = try {
                    RouteRefreshCallbackMapper.mapToDirectionsRoute(route, routeAnnotations)
                } catch (t: Throwable) {
                    errorThrowable = t
                    null
                }
                if (refreshedDirectionsRoute != null) {
                    callback.onRefresh(refreshedDirectionsRoute)
                } else {
                    callback.onError(
                        RouteRefreshError(
                            message = "Failed to read refresh response",
                            throwable = errorThrowable ?: Exception(
                                "Message=[${response.message()}]; " +
                                    "errorBody = [${response.errorBody()}];" +
                                    "refresh route = [$routeAnnotations]"
                            )
                        )
                    )
                }
            }

            override fun onFailure(call: Call<DirectionsRefreshResponse>, t: Throwable) {
                callback.onError(RouteRefreshError(throwable = t))
            }
        })
        return requestId
    }

    /**
     * Cancels a specific route refresh request.
     *
     * @see [getRouteRefresh]
     */
    override fun cancelRouteRefreshRequest(requestId: Int) {
        val request = refreshRequests.remove(requestId)
        if (request != null) {
            request.cancelCall()
        } else {
            logger.w(
                TAG,
                Message("Trying to cancel non-existing refresh request with id '$requestId'")
            )
        }
    }
}
