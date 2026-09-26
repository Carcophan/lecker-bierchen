package com.picscan.app.data.service

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class BreweryLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val cityOrRegion: String? = null
)

object BreweryLocationService {

    private const val TAG = "BreweryLocationService"
    private val memoryCache = mutableMapOf<String, BreweryLocation>()

    /**
     * Resolves an origin or address string to geographic coordinates.
     * Uses Android's native Geocoder first, then falls back to OpenStreetMap Nominatim.
     */
    suspend fun resolveLocation(
        context: Context,
        query: String,
        brandOrBrewery: String? = null
    ): BreweryLocation? = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return@withContext null

        val cacheKey = "${brandOrBrewery?.trim().orEmpty()}_$trimmedQuery".lowercase(Locale.ROOT)
        memoryCache[cacheKey]?.let { return@withContext it }

        // Attempt 1: Android native Geocoder with full brewery + location query if available
        if (brandOrBrewery != null && brandOrBrewery.isNotBlank()) {
            val fullQuery = "$brandOrBrewery, $trimmedQuery"
            geocodeWithAndroid(context, fullQuery)?.let {
                memoryCache[cacheKey] = it
                return@withContext it
            }
        }

        // Attempt 2: Android native Geocoder with location query only
        geocodeWithAndroid(context, trimmedQuery)?.let {
            memoryCache[cacheKey] = it
            return@withContext it
        }

        // Attempt 3: OpenStreetMap Nominatim API Fallback
        geocodeWithNominatim(trimmedQuery, brandOrBrewery)?.let {
            memoryCache[cacheKey] = it
            return@withContext it
        }

        null
    }

    private suspend fun geocodeWithAndroid(context: Context, locationName: String): BreweryLocation? {
        if (!Geocoder.isPresent()) {
            Log.w(TAG, "Geocoder is not present on this device/system")
            return null
        }

        return try {
            val geocoder = Geocoder(context, Locale.GERMANY)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(locationName, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val address = addresses.firstOrNull()
                            if (address != null && continuation.isActive) {
                                continuation.resume(
                                    BreweryLocation(
                                        latitude = address.latitude,
                                        longitude = address.longitude,
                                        displayName = address.getAddressLine(0) ?: locationName,
                                        cityOrRegion = address.locality ?: address.subAdminArea ?: address.adminArea
                                    )
                                )
                            } else if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "Geocoder listener error: $errorMessage")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(locationName, 1)
                val address = addresses?.firstOrNull()
                if (address != null) {
                    BreweryLocation(
                        latitude = address.latitude,
                        longitude = address.longitude,
                        displayName = address.getAddressLine(0) ?: locationName,
                        cityOrRegion = address.locality ?: address.subAdminArea ?: address.adminArea
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Android Geocoder failed for '$locationName': ${e.message}")
            null
        }
    }

    internal fun geocodeWithNominatim(locationQuery: String, brandOrBrewery: String? = null): BreweryLocation? {
        return try {
            // Clean up special characters and brackets for query
            val cleanedQuery = locationQuery
                .replace("(", " ")
                .replace(")", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val encoded = URLEncoder.encode(cleanedQuery, "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1&addressdetails=1"
            val url = URL(urlString)

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "LeckerBierchenAndroidApp/1.0 (contact: github.com/Carcophan/lecker-bierchen)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val jsonArray = Json.parseToJsonElement(response).jsonArray

                if (jsonArray.isNotEmpty()) {
                    val firstItem = jsonArray[0].jsonObject
                    val lat = firstItem["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val lon = firstItem["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val displayName = firstItem["display_name"]?.jsonPrimitive?.content ?: locationQuery

                    if (lat != null && lon != null) {
                        return BreweryLocation(
                            latitude = lat,
                            longitude = lon,
                            displayName = displayName,
                            cityOrRegion = locationQuery.split(",").firstOrNull()?.trim()
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim geocoding failed for '$locationQuery': ${e.message}")
            null
        }
    }
}
