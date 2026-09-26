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

    private val PREDEFINED_LOCATIONS = mapOf(
        // Germany - Major Beer Hubs
        "münchen" to BreweryLocation(48.137154, 11.576124, "München, Bayern, Deutschland", "München"),
        "munich" to BreweryLocation(48.137154, 11.576124, "München, Bayern, Deutschland", "München"),
        "erding" to BreweryLocation(48.306032, 11.906871, "Erding, Bayern, Deutschland", "Erding"),
        "freising" to BreweryLocation(48.402778, 11.748889, "Freising, Bayern, Deutschland", "Freising"),
        "bamberg" to BreweryLocation(49.898813, 10.902764, "Bamberg, Bayern, Deutschland", "Bamberg"),
        "kulmbach" to BreweryLocation(50.107778, 11.455000, "Kulmbach, Bayern, Deutschland", "Kulmbach"),
        "nürnberg" to BreweryLocation(49.452030, 11.076750, "Nürnberg, Bayern, Deutschland", "Nürnberg"),
        "nuernberg" to BreweryLocation(49.452030, 11.076750, "Nürnberg, Bayern, Deutschland", "Nürnberg"),
        "augsburg" to BreweryLocation(48.370545, 10.897790, "Augsburg, Bayern, Deutschland", "Augsburg"),
        "regensburg" to BreweryLocation(49.013432, 12.101624, "Regensburg, Bayern, Deutschland", "Regensburg"),
        "tegernsee" to BreweryLocation(47.712170, 11.758410, "Tegernsee, Bayern, Deutschland", "Tegernsee"),
        "ayinger" to BreweryLocation(47.971944, 11.780556, "Aying, Bayern, Deutschland", "Aying"),
        "aying" to BreweryLocation(47.971944, 11.780556, "Aying, Bayern, Deutschland", "Aying"),
        "köln" to BreweryLocation(50.937531, 6.960279, "Köln, NRW, Deutschland", "Köln"),
        "koeln" to BreweryLocation(50.937531, 6.960279, "Köln, NRW, Deutschland", "Köln"),
        "cologne" to BreweryLocation(50.937531, 6.960279, "Köln, NRW, Deutschland", "Köln"),
        "düsseldorf" to BreweryLocation(51.227741, 6.773456, "Düsseldorf, NRW, Deutschland", "Düsseldorf"),
        "duesseldorf" to BreweryLocation(51.227741, 6.773456, "Düsseldorf, NRW, Deutschland", "Düsseldorf"),
        "dortmund" to BreweryLocation(51.513587, 7.465298, "Dortmund, NRW, Deutschland", "Dortmund"),
        "krombach" to BreweryLocation(50.998611, 7.962778, "Kreuztal-Krombach, NRW, Deutschland", "Krombach"),
        "kreuztal" to BreweryLocation(50.998611, 7.962778, "Kreuztal, NRW, Deutschland", "Kreuztal"),
        "bitburg" to BreweryLocation(49.974444, 6.524444, "Bitburg, Rheinland-Pfalz, Deutschland", "Bitburg"),
        "warstein" to BreweryLocation(51.446667, 8.356111, "Warstein, NRW, Deutschland", "Warstein"),
        "veltin" to BreweryLocation(51.298056, 8.163333, "Meschede-Grevenstein, NRW, Deutschland", "Grevenstein"),
        "grevenstein" to BreweryLocation(51.298056, 8.163333, "Grevenstein, NRW, Deutschland", "Grevenstein"),
        "flensburg" to BreweryLocation(54.787711, 9.435707, "Flensburg, Schleswig-Holstein, Deutschland", "Flensburg"),
        "jever" to BreweryLocation(53.573611, 7.901667, "Jever, Niedersachsen, Deutschland", "Jever"),
        "bremen" to BreweryLocation(53.079296, 8.801694, "Bremen, Deutschland", "Bremen"),
        "hamburg" to BreweryLocation(53.551086, 9.993682, "Hamburg, Deutschland", "Hamburg"),
        "berlin" to BreweryLocation(52.520008, 13.404954, "Berlin, Deutschland", "Berlin"),
        "radeberg" to BreweryLocation(51.116667, 13.916667, "Radeberg, Sachsen, Deutschland", "Radeberg"),
        "dresden" to BreweryLocation(51.050409, 13.737262, "Dresden, Sachsen, Deutschland", "Dresden"),
        "leipzig" to BreweryLocation(51.339695, 12.373075, "Leipzig, Sachsen, Deutschland", "Leipzig"),
        "einbeck" to BreweryLocation(51.816667, 9.866667, "Einbeck, Niedersachsen, Deutschland", "Einbeck"),
        "stuttgart" to BreweryLocation(48.775846, 9.182932, "Stuttgart, Baden-Württemberg, Deutschland", "Stuttgart"),
        "frankfurt" to BreweryLocation(50.110922, 8.682127, "Frankfurt am Main, Hessen, Deutschland", "Frankfurt"),
        "bayern" to BreweryLocation(48.790447, 11.497890, "Bayern, Deutschland", "Bayern"),
        "deutschland" to BreweryLocation(51.165691, 10.451526, "Deutschland", "Deutschland"),
        "germany" to BreweryLocation(51.165691, 10.451526, "Deutschland", "Deutschland"),

        // Czech Republic
        "pilsen" to BreweryLocation(49.747474, 13.377636, "Plzeň (Pilsen), Tschechien", "Plzeň"),
        "plzeň" to BreweryLocation(49.747474, 13.377636, "Plzeň (Pilsen), Tschechien", "Plzeň"),
        "budweis" to BreweryLocation(48.974466, 14.474342, "České Budějovice (Budweis), Tschechien", "Budweis"),
        "české budějovice" to BreweryLocation(48.974466, 14.474342, "České Budějovice, Tschechien", "Budweis"),
        "prag" to BreweryLocation(50.075538, 14.437800, "Prag, Tschechien", "Prag"),
        "prague" to BreweryLocation(50.075538, 14.437800, "Prag, Tschechien", "Prag"),
        "tschechien" to BreweryLocation(49.817492, 15.472962, "Tschechien", "Tschechien"),

        // Belgium
        "brüssel" to BreweryLocation(50.850346, 4.351721, "Brüssel, Belgien", "Brüssel"),
        "brussels" to BreweryLocation(50.850346, 4.351721, "Brüssel, Belgien", "Brüssel"),
        "leuven" to BreweryLocation(50.879844, 4.700518, "Leuven, Belgien", "Leuven"),
        "brügge" to BreweryLocation(51.209348, 3.224699, "Brügge, Belgien", "Brügge"),
        "antwerpen" to BreweryLocation(51.219448, 4.402464, "Antwerpen, Belgien", "Antwerpen"),
        "chimay" to BreweryLocation(50.048611, 4.313889, "Chimay, Belgien", "Chimay"),
        "belgien" to BreweryLocation(50.503887, 4.469936, "Belgien", "Belgien"),

        // Ireland & UK
        "dublin" to BreweryLocation(53.349805, -6.260310, "Dublin, Irland", "Dublin"),
        "kilkenny" to BreweryLocation(52.654146, -7.244788, "Kilkenny, Irland", "Kilkenny"),
        "cork" to BreweryLocation(51.898514, -8.475604, "Cork, Irland", "Cork"),
        "irland" to BreweryLocation(53.142367, -7.692054, "Irland", "Irland"),
        "ireland" to BreweryLocation(53.142367, -7.692054, "Irland", "Irland"),
        "london" to BreweryLocation(51.507351, -0.127758, "London, UK", "London"),
        "edinburgh" to BreweryLocation(55.953251, -3.188267, "Edinburgh, Schottland", "Edinburgh"),

        // Other European & Worldwide
        "amsterdam" to BreweryLocation(52.367573, 4.904138, "Amsterdam, Niederlande", "Amsterdam"),
        "niederlande" to BreweryLocation(52.132633, 5.291266, "Niederlande", "Niederlande"),
        "wien" to BreweryLocation(48.208174, 16.373819, "Wien, Österreich", "Wien"),
        "salzburg" to BreweryLocation(47.809490, 13.055010, "Salzburg, Österreich", "Salzburg"),
        "österreich" to BreweryLocation(47.516231, 14.550072, "Österreich", "Österreich"),
        "zürich" to BreweryLocation(47.376887, 8.541694, "Zürich, Schweiz", "Zürich"),
        "schweiz" to BreweryLocation(46.818188, 8.227512, "Schweiz", "Schweiz"),
        "kopenhagen" to BreweryLocation(55.676098, 12.568337, "Kopenhagen, Dänemark", "Kopenhagen"),
        "dänemark" to BreweryLocation(56.263920, 9.501785, "Dänemark", "Dänemark"),
        "mexiko" to BreweryLocation(23.634501, -102.552784, "Mexiko", "Mexiko"),
        "mexico" to BreweryLocation(23.634501, -102.552784, "Mexiko", "Mexiko"),
        "usa" to BreweryLocation(37.090240, -95.712891, "USA", "USA"),
        "japan" to BreweryLocation(36.204824, 138.252924, "Japan", "Japan"),
        "tokio" to BreweryLocation(35.676192, 139.650311, "Tokio, Japan", "Tokio"),
        "tokyo" to BreweryLocation(35.676192, 139.650311, "Tokio, Japan", "Tokio"),
        "qingdao" to BreweryLocation(36.067108, 120.382609, "Qingdao, China", "Qingdao"),
        "tsingtao" to BreweryLocation(36.067108, 120.382609, "Qingdao, China", "Qingdao"),
        "china" to BreweryLocation(35.861660, 104.195397, "China", "China"),
        "australien" to BreweryLocation(-25.274398, 133.775136, "Australien", "Australien")
    )

    fun findPredefinedLocation(query: String, brandOrBrewery: String? = null): BreweryLocation? {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return null
        val lowerQuery = trimmedQuery.lowercase(Locale.ROOT)
        val lowerBrand = brandOrBrewery?.lowercase(Locale.ROOT).orEmpty()
        for ((key, loc) in PREDEFINED_LOCATIONS) {
            if (lowerQuery.contains(key) || lowerBrand.contains(key)) {
                return loc
            }
        }
        return null
    }

    /**
     * Resolves an origin or address string to geographic coordinates.
     * Uses static lookup first, then Android native Geocoder, then OpenStreetMap Nominatim.
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

        // Fast static match
        findPredefinedLocation(trimmedQuery, brandOrBrewery)?.let {
            memoryCache[cacheKey] = it
            return@withContext it
        }

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
