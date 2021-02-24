package com.mapbox.navigation.examples.features

import android.graphics.Color
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.QueryFeaturesCallback
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.FillLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource

class HighlightBuildingsExample(
    private val mapboxMap: MapboxMap,
    private val style: Style
) {

    fun selectBuilding(point: Point) {
        val screenCoordinate = mapboxMap.pixelForCoordinate(point)
        val options = RenderedQueryOptions(listOf("building"), null)

        mapboxMap.queryRenderedFeatures(screenCoordinate, options, QueryFeaturesCallback { expected ->
            val features = expected.value ?: emptyList<Feature>()
            if (features.isNotEmpty()) {
                buildingLayer(style, features)
            }
        })
    }

    private fun buildingLayer(
        style: Style,
        features: List<Feature>
    ) {
        val layerId = BUILDING_LAYER_ID
        val sourceId = BUILDING_SOURCE_ID
        if (style.styleLayerExists(layerId)) {
            style.removeStyleLayer(layerId)
        }

        style.addSource(GeoJsonSource.Builder(sourceId)
            .featureCollection(FeatureCollection.fromFeatures(features))
            .build())

        val layer = FillLayer(layerId, sourceId)
            .fillColor(Color.RED)
            .fillOutlineColor(Color.BLACK)

        style.addLayer(layer)
    }

    companion object {
        private const val BUILDING_LAYER_ID = "mapbox-navigation-building-layer"
        private const val BUILDING_SOURCE_ID = "mapbox-navigation-building-source"
    }
}
