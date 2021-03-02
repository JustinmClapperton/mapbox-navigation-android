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
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource

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

        if (style.styleSourceExists(sourceId)) {
            (style.getSource(sourceId) as GeoJsonSource)
                .featureCollection(FeatureCollection.fromFeatures(features))
        } else {
            style.addSource(GeoJsonSource.Builder(sourceId)
                .featureCollection(FeatureCollection.fromFeatures(features))
                .build())
        }

        if (style.styleLayerExists(layerId)) {
            (style.getLayer(layerId) as FillExtrusionLayer)
                .fillExtrusionHeight(10.0)
        } else {
            style.addLayer(FillExtrusionLayer(layerId, sourceId)
                .fillExtrusionColor(Color.argb(0xB2, 0xD6, 0x02, 0xEE))
                .fillExtrusionHeight(10.0))
        }
    }

    companion object {
        private const val BUILDING_LAYER_ID = "navigation-building-layer"
        private const val BUILDING_SOURCE_ID = "navigation-building-source"
    }
}
