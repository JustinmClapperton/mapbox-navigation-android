package com.mapbox.navigation.ui.maps.route.line.model

import com.mapbox.maps.extension.style.layers.properties.generated.Visibility

/**
 * A state representing the side effects for updating the route visibility.
 *
 * @param layerVisibilityModifications a collection of visibility modifications
 */
class LayerVisibilityUpdate internal constructor(
    private val layerVisibilityModifications: List<Pair<String, Visibility>>
) {
    /**
     * @return a collection of visibility modifications
     */
    fun getLayerVisibilityChanges(): List<Pair<String, Visibility>> =
        layerVisibilityModifications
}
