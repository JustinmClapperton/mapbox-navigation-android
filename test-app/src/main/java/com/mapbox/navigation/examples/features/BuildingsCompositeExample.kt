package com.mapbox.navigation.examples.features

import android.graphics.Color
import android.util.Log
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.QueryFeaturesCallback
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.PropertyValue
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource

class BuildingsCompositeExample(
    private val style: Style
) {
    var fillExtrusionColor = Color.argb(0xB2, 0xAA, 0xAA, 0xAA)

    fun toggleBuildings() {
        val layer = style.getLayer(BUILDING_LAYER_ID)
        if (layer == null) {
            add3dBuildings()
        } else {
            style.removeStyleLayer(BUILDING_LAYER_ID)
        }
    }

    private fun add3dBuildings() {
        val layer = FillExtrusionLayer(BUILDING_LAYER_ID, "composite")
            .sourceLayer(BUILDING_SOURCE_ID)
            .fillExtrusionColor(fillExtrusionColor)
            .fillExtrusionBase(Expression.get("min-height"))
            .fillExtrusionHeight(Expression.get("height"))
        style.addLayer(layer)
    }

    companion object {
        private const val BUILDING_LAYER_ID = "building-3d-layer"
        private const val BUILDING_SOURCE_ID = "building"
    }
}
