package com.mapbox.navigation.base.options

import java.net.URI

/**
 * Defines options for routing tiles endpoint and storage configuration.
 *
 * Routing tiles are stored on-device and used for map-matching (enhanced location production),
 * offline routing, Electronic Horizon generation, and other.
 *
 * @param tilesBaseUri scheme and host, for example `"https://api.mapbox.com"`
 * @param tilesDataset string built out of `<account>[.<graph>]` variables.
 * Account can be `mapbox` for default datasets or your username for other.
 * Graph can be left blank if you don't target any custom datasets.
 * @param tilesProfile profile of the dataset.
 * One of (driving|driving-traffic|walking|cycling|truck).
 * @param tilesVersion version of tiles, chosen automatically if empty
 * @param filePath used for storing road network tiles
 * @param minDaysBetweenServerAndLocalTilesVersion is the minimum time in days between local version of tiles
 * and latest on the server to consider using the latest version of routing tiles from the server.
 * **As updating tiles frequently consumes considerably energy and bandwidth**.
 * Note that this only works if [tilesVersion] is empty.
 */
class RoutingTilesOptions private constructor(
    val tilesBaseUri: URI,
    val tilesDataset: String,
    val tilesProfile: String,
    val tilesVersion: String,
    val filePath: String?,
    val minDaysBetweenServerAndLocalTilesVersion: Int
) {
    /**
     * @return the builder that created the [RoutingTilesOptions]
     */
    fun toBuilder(): Builder = Builder().apply {
        tilesBaseUri(tilesBaseUri)
        tilesDataset(tilesDataset)
        tilesProfile(tilesProfile)
        tilesVersion(tilesVersion)
        filePath(filePath)
        minDaysBetweenServerAndLocalTilesVersion(minDaysBetweenServerAndLocalTilesVersion)
    }

    /**
     * Regenerate whenever a change is made
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoutingTilesOptions

        if (tilesBaseUri != other.tilesBaseUri) return false
        if (tilesDataset != other.tilesDataset) return false
        if (tilesProfile != other.tilesProfile) return false
        if (tilesVersion != other.tilesVersion) return false
        if (filePath != other.filePath) return false
        if (minDaysBetweenServerAndLocalTilesVersion
            != other.minDaysBetweenServerAndLocalTilesVersion
        ) return false

        return true
    }

    /**
     * Regenerate whenever a change is made
     */
    override fun hashCode(): Int {
        var result = tilesBaseUri.hashCode()
        result = 31 * result + tilesDataset.hashCode()
        result = 31 * result + tilesProfile.hashCode()
        result = 31 * result + tilesVersion.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + minDaysBetweenServerAndLocalTilesVersion.hashCode()
        return result
    }

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String {
        return "RoutingTilesOptions(" +
            "tilesBaseUri=$tilesBaseUri, " +
            "tilesDataset='$tilesDataset', " +
            "tilesProfile='$tilesProfile', " +
            "tilesVersion='$tilesVersion', " +
            "filePath=$filePath, " +
            "minDaysBetweenServerAndLocalTilesVersion=$minDaysBetweenServerAndLocalTilesVersion" +
            ")"
    }

    /**
     * Builder for [RoutingTilesOptions]. You must choose a [filePath]
     * for this to be built successfully.
     */
    class Builder {

        private var tilesBaseUri: URI = URI("https://api.mapbox.com")
        private var tilesDataset: String = "mapbox"
        private var tilesProfile: String = "driving-traffic"
        private var tilesVersion: String = ""
        private var filePath: String? = null
        private var minDaysBetweenServerAndLocalTilesVersion: Int = 56 // 8 weeks

        /**
         * Override the routing tiles base endpoint with a [URI].
         *
         * Expects scheme and host, for example `"https://api.mapbox.com"`.
         */
        fun tilesBaseUri(tilesBaseUri: URI): Builder =
            apply {
                val validUri = tilesBaseUri.isAbsolute &&
                    tilesBaseUri.host.isNotEmpty() &&
                    tilesBaseUri.fragment == null &&
                    tilesBaseUri.path.let { it.isNullOrEmpty() || it.matches("/*".toRegex()) } &&
                    tilesBaseUri.query == null
                check(validUri) {
                    throw IllegalArgumentException(
                        "The base URI should only contain the scheme and host."
                    )
                }
                this.tilesBaseUri = tilesBaseUri
            }

        /**
         * String built out of `<account>[.<graph>]` variables.
         *
         * Account can be `mapbox` for default datasets or your username for other.
         *
         * Graph can be left blank if you don't have any no custom datasets.
         *
         * Defaults to `mapbox`.
         */
        fun tilesDataset(tilesDataset: String): Builder =
            apply { this.tilesDataset = tilesDataset }

        /**
         * Profile of the dataset. One of (driving|driving-traffic|walking|cycling|truck).
         *
         * Defaults to `driving-traffic`.
         */
        fun tilesProfile(tilesProfile: String): Builder =
            apply { this.tilesProfile = tilesProfile }

        /**
         * Override the default routing tiles version.
         *
         * You can find available version by calling:
         * `<host>/route-tiles/v2/<account>[.<graph>]/<profile>/versions?access_token=<your_token>`
         *
         * where profile is one of (driving|driving-traffic|walking|cycling|truck).
         */
        fun tilesVersion(version: String): Builder =
            apply { this.tilesVersion = version }

        /**
         * Creates a custom file path to store the road network tiles.
         */
        fun filePath(filePath: String?): Builder =
            apply { this.filePath = filePath }

        /**
         * Override minimum time in days between local version of tiles and latest on the server
         * to consider using the latest version of routing tiles from the server. This only works
         * if tiles version is empty.
         *
         * Note that **updating tiles frequently consumes considerably energy and bandwidth**.
         *
         * Default value is **56** (8 weeks).
         *
         * @param minDaysBetweenServerAndLocalTilesVersion must be greater than or equal to 0
         * @see [tilesVersion]
         */
        fun minDaysBetweenServerAndLocalTilesVersion(
            minDaysBetweenServerAndLocalTilesVersion: Int
        ): Builder =
            apply {
                check(minDaysBetweenServerAndLocalTilesVersion >= 0) {
                    "minDaysBetweenServerAndLocalTilesVersion must be greater than or equal to 0"
                }
                this.minDaysBetweenServerAndLocalTilesVersion =
                    minDaysBetweenServerAndLocalTilesVersion
            }

        /**
         * Build the [RoutingTilesOptions]
         */
        fun build(): RoutingTilesOptions {
            return RoutingTilesOptions(
                tilesBaseUri = tilesBaseUri,
                tilesDataset = tilesDataset,
                tilesProfile = tilesProfile,
                tilesVersion = tilesVersion,
                filePath = filePath,
                minDaysBetweenServerAndLocalTilesVersion = minDaysBetweenServerAndLocalTilesVersion
            )
        }
    }
}
