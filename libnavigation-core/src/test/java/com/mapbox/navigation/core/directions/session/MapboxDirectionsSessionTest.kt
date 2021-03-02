package com.mapbox.navigation.core.directions.session

import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.base.common.logger.Logger
import com.mapbox.navigation.base.route.Router
import com.mapbox.navigation.core.NavigationComponentProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MapboxDirectionsSessionTest {

    private lateinit var session: MapboxDirectionsSession

    private val router: Router = mockk(relaxUnitFun = true)
    private val logger: Logger = mockk(relaxUnitFun = true)
    private val routeOptions: RouteOptions = mockk(relaxUnitFun = true)
    private val routesRequestCallback: RoutesRequestCallback = mockk(relaxUnitFun = true)
    private val observer: RoutesObserver = mockk(relaxUnitFun = true)
    private val route: DirectionsRoute = mockk(relaxUnitFun = true)
    private val routes: List<DirectionsRoute> = listOf(route)
    private lateinit var callback: Router.Callback

    @Before
    fun setUp() {
        val routeOptionsBuilder: RouteOptions.Builder = mockk(relaxUnitFun = true)
        every { routeOptionsBuilder.waypointIndices(any()) } returns routeOptionsBuilder
        every { routeOptionsBuilder.waypointNames(any()) } returns routeOptionsBuilder
        every { routeOptionsBuilder.waypointTargets(any()) } returns routeOptionsBuilder
        every { routeOptionsBuilder.build() } returns routeOptions
        every { routeOptions.toBuilder() } returns routeOptionsBuilder
        every { routeOptions.waypointIndices() } returns ""
        every { routeOptions.waypointNames() } returns ""
        every { routeOptions.waypointTargets() } returns ""
        val routeBuilder: DirectionsRoute.Builder = mockk(relaxUnitFun = true)
        every { route.toBuilder() } returns routeBuilder
        every { routeBuilder.routeOptions(any()) } returns routeBuilder
        every { routeBuilder.build() } returns route

        val listener = slot<Router.Callback>()
        every { router.getRoute(routeOptions, capture(listener)) } answers {
            callback = listener.captured
        }
        every { routes[0].routeOptions() } returns routeOptions
        mockkObject(NavigationComponentProvider)
        every { routesRequestCallback.onRoutesReady(any()) } answers {
            this.value
        }
        session = MapboxDirectionsSession(router, logger)
    }

    @Test
    fun initialState() {
        assertNull(session.getPrimaryRouteOptions())
        assertEquals(session.routes, emptyList<DirectionsRoute>())
    }

    @Test
    fun `route response - success`() {
        session.requestRoutes(routeOptions, routesRequestCallback)
        callback.onResponse(routes)

        verify(exactly = 1) { routesRequestCallback.onRoutesReady(routes) }
    }

    @Test
    fun `route response - failure`() {
        val throwable: Throwable = mockk()
        session.requestRoutes(routeOptions, routesRequestCallback)
        callback.onFailure(throwable)

        verify(exactly = 1) {
            routesRequestCallback.onRoutesRequestFailure(throwable, routeOptions)
        }
    }

    @Test
    fun `route response - canceled`() {
        session.requestRoutes(routeOptions, routesRequestCallback)
        callback.onCanceled()

        verify(exactly = 1) {
            routesRequestCallback.onRoutesRequestCanceled(routeOptions)
        }
    }

    @Test
    fun getRouteOptions() {
        session.routes = routes
        assertEquals(routeOptions, session.getPrimaryRouteOptions())
    }

    @Test
    fun cancel() {
        session.cancel()
        verify { router.cancelAll() }
    }

    @Test
    fun shutDown() {
        session.shutdown()
        verify { router.shutdown() }
    }

    @Test
    fun `when route set, observer notified`() {
        session.registerRoutesObserver(observer)
        session.routes = routes
        verify(exactly = 1) { observer.onRoutesChanged(routes) }
    }

    @Test
    fun `when route cleared, observer notified`() {
        session.registerRoutesObserver(observer)
        session.routes = routes
        session.routes = emptyList()
        verify(exactly = 1) { observer.onRoutesChanged(emptyList()) }
    }

    @Test
    fun `when new route available, observer notified`() {
        session.registerRoutesObserver(observer)
        session.routes = routes
        val newRoutes: List<DirectionsRoute> = listOf(mockk())
        every { newRoutes[0].routeOptions() } returns routeOptions
        session.routes = newRoutes
        verify(exactly = 1) { observer.onRoutesChanged(newRoutes) }
    }

    @Test
    fun `setting a route does not impact ongoing route request`() {
        session.requestRoutes(routeOptions, routesRequestCallback)
        session.routes = routes
        verify(exactly = 0) { router.cancelAll() }
    }

    @Test
    fun unregisterAllRouteObservers() {
        session.registerRoutesObserver(observer)
        session.unregisterAllRoutesObservers()
        session.routes = routes

        verify(exactly = 0) { observer.onRoutesChanged(any()) }
    }
}
