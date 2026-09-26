package com.picscan.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.picscan.app.data.model.BeerListType
import com.picscan.app.data.model.BeerVerdict
import com.picscan.app.data.model.SavedBeerItem
import com.picscan.app.data.service.BreweryLocationService
import com.picscan.app.ui.viewmodel.ScannerViewModel
import com.picscan.app.util.ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun getVerdictColorHex(verdict: BeerVerdict): String {
    return when (verdict) {
        BeerVerdict.HOPFENBOMBE -> "#00E676"     // Vibrant Emerald
        BeerVerdict.LECKER_BIERCHEN -> "#FFB300" // Golden Amber
        BeerVerdict.WEGBIER -> "#00B0FF"         // Vivid Cyan
        BeerVerdict.PENNERGLUECK -> "#FFAB00"    // Deep Orange
        BeerVerdict.PISSBRUEHE -> "#FF1744"      // Crimson Red
        BeerVerdict.NONE -> "#FFC107"            // Beer Gold
    }
}

private fun getVerdictComposeColor(verdict: BeerVerdict): Color {
    return when (verdict) {
        BeerVerdict.HOPFENBOMBE -> Color(0xFF00E676)
        BeerVerdict.LECKER_BIERCHEN -> Color(0xFFFFB300)
        BeerVerdict.WEGBIER -> Color(0xFF00B0FF)
        BeerVerdict.PENNERGLUECK -> Color(0xFFFFAB00)
        BeerVerdict.PISSBRUEHE -> Color(0xFFFF1744)
        BeerVerdict.NONE -> Color(0xFFFFC107)
    }
}

enum class GlobeMapStyle(val title: String, val icon: String, val key: String) {
    SATELLITE("Satellit", "🛰️", "satellite"),
    STREETS("Straßenkarte", "🗺️", "osm"),
    DARK("Dunkel", "🌙", "dark")
}

enum class GlobeProjectionMode(val title: String, val icon: String, val key: String) {
    GLOBE("3D Globus", "🌍", "globe"),
    FLAT("2D Karte", "🗺️", "mercator")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeerGlobeScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onBeerSelected: (SavedBeerItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allSavedBeers by viewModel.allSavedBeers.collectAsState()

    // Map style and projection
    var currentStyle by remember { mutableStateOf(GlobeMapStyle.SATELLITE) }
    var currentProjection by remember { mutableStateOf(GlobeProjectionMode.GLOBE) }

    // Filtering state
    var selectedTabFilter by remember { mutableStateOf<BeerListType?>(null) }
    var selectedVerdictFilter by remember { mutableStateOf<BeerVerdict?>(null) }
    var selectedBeerId by remember { mutableStateOf<String?>(null) }
    var isAutoRotating by remember { mutableStateOf(true) }

    // Geocoding cache and resolution tracking: beerId -> Pair(lat, lng)
    val resolvedCoords = remember { mutableStateMapOf<String, Pair<Double, Double>>() }
    var isResolvingLocations by remember { mutableStateOf(false) }
    var resolvedCount by remember { mutableIntStateOf(0) }
    var totalToResolve by remember { mutableIntStateOf(0) }

    // Resolve missing locations in background with coroutines
    LaunchedEffect(allSavedBeers) {
        val beersWithOrigin = allSavedBeers.filter { !it.origin.isNullOrBlank() }
        totalToResolve = beersWithOrigin.size

        // Pre-fill already known coordinates from SavedBeerItem
        beersWithOrigin.forEach { beer ->
            if (beer.latitude != null && beer.longitude != null) {
                resolvedCoords[beer.id] = Pair(beer.latitude, beer.longitude)
            }
        }
        resolvedCount = resolvedCoords.size

        val missing = beersWithOrigin.filter { beer -> !resolvedCoords.containsKey(beer.id) }
        if (missing.isNotEmpty()) {
            isResolvingLocations = true
            for (beer in missing) {
                val origin = beer.origin ?: continue
                val loc = BreweryLocationService.resolveLocation(context, origin, beer.brandOrProducer)
                if (loc != null) {
                    resolvedCoords[beer.id] = Pair(loc.latitude, loc.longitude)
                    viewModel.updateBeerCoordinates(beer.id, loc.latitude, loc.longitude)
                }
                resolvedCount++
            }
            isResolvingLocations = false
        }
    }

    // Filter beers based on selection
    val displayedBeers = remember(allSavedBeers, resolvedCoords.toMap(), selectedTabFilter, selectedVerdictFilter) {
        allSavedBeers.filter { beer ->
            val hasCoords = resolvedCoords.containsKey(beer.id) || (beer.latitude != null && beer.longitude != null)
            val matchesTab = selectedTabFilter == null || beer.listType == selectedTabFilter
            val matchesVerdict = selectedVerdictFilter == null || beer.beerVerdict == selectedVerdictFilter
            hasCoords && matchesTab && matchesVerdict
        }
    }

    // Selected beer item
    val selectedBeer = remember(selectedBeerId, allSavedBeers) {
        if (selectedBeerId != null) allSavedBeers.find { it.id == selectedBeerId } else null
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Prepare JSON data for markers on the dynamic LOD 3D Globe
    val globeMarkersJson = remember(displayedBeers, resolvedCoords.toMap()) {
        val jsonArray = JSONArray()
        for (beer in displayedBeers) {
            val coords = resolvedCoords[beer.id] ?: (if (beer.latitude != null && beer.longitude != null) Pair(beer.latitude, beer.longitude) else null)
            if (coords != null) {
                val obj = JSONObject().apply {
                    put("id", beer.id)
                    put("name", beer.name)
                    put("brand", beer.brandOrProducer ?: "")
                    put("origin", beer.origin ?: "")
                    put("lat", coords.first)
                    put("lng", coords.second)
                    put("verdict", beer.beerVerdict.name)
                    put("verdictEmoji", beer.beerVerdict.emoji)
                    put("verdictTitle", beer.beerVerdict.title)
                    put("color", getVerdictColorHex(beer.beerVerdict))
                    put("rating", beer.rating.toDouble())
                    put("listType", beer.listType.name)
                }
                jsonArray.put(obj)
            }
        }
        jsonArray.toString()
    }

    // Push updated markers to web view when filter changes
    LaunchedEffect(globeMarkersJson, webViewRef) {
        webViewRef?.evaluateJavascript("if (window.updatePoints) { window.updatePoints(${JSONObject.quote(globeMarkersJson)}); }", null)
    }

    val globeHtmlContent = remember {
        generateMapLibreGlobeHtml()
    }

    Scaffold(
        containerColor = Color(0xFF080C14)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive 3D Globe with Dynamic Level-of-Detail (LOD) Tile Streaming
            MapLibreGlobeWebView(
                htmlContent = globeHtmlContent,
                markersJson = globeMarkersJson,
                onWebViewReady = { webViewRef = it },
                onBeerTapped = { beerId ->
                    selectedBeerId = beerId
                }
            )

            // Top Overlay Bar: Header, Navigation & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xEB080C14),
                                Color(0xB3080C14),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0x551E293B),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }

                        Column {
                            Text(
                                text = "🌍 3D Bier-Globus",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val locationCount = displayedBeers.mapNotNull { it.origin }.distinct().size
                            Text(
                                text = "$locationCount Standorte • ${displayedBeers.size} Biere",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Globe & Map Controls: Projection, Style, Auto-Rotate & Center
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle 3D Globe vs 2D Mercator Projection
                        IconButton(
                            onClick = {
                                currentProjection = if (currentProjection == GlobeProjectionMode.GLOBE) GlobeProjectionMode.FLAT else GlobeProjectionMode.GLOBE
                                webViewRef?.evaluateJavascript(
                                    "if (window.setProjection) { window.setProjection('${currentProjection.key}'); }",
                                    null
                                )
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (currentProjection == GlobeProjectionMode.GLOBE) Color(0x551E293B) else Color(0x88FFB300),
                                contentColor = if (currentProjection == GlobeProjectionMode.GLOBE) Color.White else Color.Black
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(currentProjection.icon, fontSize = 15.sp)
                        }

                        // Cycle tile styles (Satellit -> Straßenkarte -> Dunkel)
                        IconButton(
                            onClick = {
                                currentStyle = when (currentStyle) {
                                    GlobeMapStyle.SATELLITE -> GlobeMapStyle.STREETS
                                    GlobeMapStyle.STREETS -> GlobeMapStyle.DARK
                                    GlobeMapStyle.DARK -> GlobeMapStyle.SATELLITE
                                }
                                webViewRef?.evaluateJavascript(
                                    "if (window.setMapStyle) { window.setMapStyle('${currentStyle.key}'); }",
                                    null
                                )
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0x551E293B),
                                contentColor = Color(0xFFFFB300)
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(currentStyle.icon, fontSize = 15.sp)
                        }

                        // Auto-rotation toggle
                        IconButton(
                            onClick = {
                                isAutoRotating = !isAutoRotating
                                webViewRef?.evaluateJavascript(
                                    "if (window.setAutoRotation) { window.setAutoRotation($isAutoRotating); }",
                                    null
                                )
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isAutoRotating) Color(0x88FFB300) else Color(0x551E293B),
                                contentColor = if (isAutoRotating) Color.Black else Color.White
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isAutoRotating) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Auto-Rotation umschalten",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Reset camera overview
                        IconButton(
                            onClick = {
                                selectedBeerId = null
                                webViewRef?.evaluateJavascript(
                                    "if (window.resetView) { window.resetView(); }",
                                    null
                                )
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0x551E293B),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.CenterFocusStrong,
                                contentDescription = "Ansicht zentrieren",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Row: Tab Filter & Verdict Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTabFilter == null,
                            onClick = { selectedTabFilter = null },
                            label = { Text("Alle (${allSavedBeers.count { resolvedCoords.containsKey(it.id) || it.latitude != null }})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFB300),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x401E293B),
                                labelColor = Color.White
                            )
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedTabFilter == BeerListType.KNOWN,
                            onClick = {
                                selectedTabFilter = if (selectedTabFilter == BeerListType.KNOWN) null else BeerListType.KNOWN
                            },
                            label = { Text("🍺 Kenne ich") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFB300),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x401E293B),
                                labelColor = Color.White
                            )
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedTabFilter == BeerListType.WISHLIST,
                            onClick = {
                                selectedTabFilter = if (selectedTabFilter == BeerListType.WISHLIST) null else BeerListType.WISHLIST
                            },
                            label = { Text("📌 Will ich") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFB300),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x401E293B),
                                labelColor = Color.White
                            )
                        )
                    }

                    val verdicts = listOf(
                        BeerVerdict.HOPFENBOMBE,
                        BeerVerdict.LECKER_BIERCHEN,
                        BeerVerdict.WEGBIER,
                        BeerVerdict.PENNERGLUECK,
                        BeerVerdict.PISSBRUEHE
                    )

                    items(verdicts) { verdict ->
                        val count = allSavedBeers.count {
                            it.beerVerdict == verdict && (resolvedCoords.containsKey(it.id) || it.latitude != null)
                        }
                        if (count > 0 || selectedVerdictFilter == verdict) {
                            FilterChip(
                                selected = selectedVerdictFilter == verdict,
                                onClick = {
                                    selectedVerdictFilter = if (selectedVerdictFilter == verdict) null else verdict
                                },
                                label = { Text("${verdict.emoji} ${verdict.title} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = getVerdictComposeColor(verdict),
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0x401E293B),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Resolving progress indicator
                AnimatedVisibility(
                    visible = isResolvingLocations,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x800F172A),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFB300)
                            )
                            Text(
                                text = "Lokalisiere Brauerei-Standorte... ($resolvedCount/$totalToResolve)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // Bottom Area: Floating Beer Preview Card or Quick-Select Carousel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xD9080C14),
                                Color(0xF2080C14)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // If a beer is selected on the globe:
                AnimatedVisibility(
                    visible = selectedBeer != null,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    if (selectedBeer != null) {
                        GlobeSelectedBeerCard(
                            beer = selectedBeer!!,
                            onClose = {
                                selectedBeerId = null
                                webViewRef?.evaluateJavascript("if (window.unhighlightMarkers) { window.unhighlightMarkers(); }", null)
                            },
                            onOpenDetails = {
                                viewModel.selectSavedBeer(selectedBeer!!)
                                onBeerSelected(selectedBeer!!)
                            },
                            onZoomIn = {
                                val coords = resolvedCoords[selectedBeer!!.id] ?: (if (selectedBeer!!.latitude != null && selectedBeer!!.longitude != null) Pair(selectedBeer!!.latitude!!, selectedBeer!!.longitude!!) else null)
                                if (coords != null) {
                                    webViewRef?.evaluateJavascript(
                                        "if (window.focusBeerLocation) { window.focusBeerLocation(${coords.first}, ${coords.second}, '${selectedBeer!!.id}', 16); }",
                                        null
                                    )
                                }
                            },
                            onNavigateMaps = {
                                val origin = selectedBeer!!.origin.orEmpty()
                                val brand = selectedBeer!!.brandOrProducer.orEmpty()
                                val coords = resolvedCoords[selectedBeer!!.id]
                                val uri = if (coords != null) {
                                    Uri.parse("geo:0,0?q=${coords.first},${coords.second}(${Uri.encode(brand.ifBlank { selectedBeer!!.name })})")
                                } else {
                                    Uri.parse("geo:0,0?q=${Uri.encode("$brand $origin")}")
                                }
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val webIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("$brand $origin")}")
                                    )
                                    context.startActivity(webIntent)
                                }
                            }
                        )
                    }
                }

                // Horizontal Carousel of all beers for quick access
                if (displayedBeers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Biere auf der 3D-Weltkugel:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "${displayedBeers.size} Biere",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFB300)
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(displayedBeers, key = { it.id }) { beer ->
                                val isSelected = beer.id == selectedBeerId
                                GlobeBeerCarouselItem(
                                    beer = beer,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedBeerId = beer.id
                                        val coords = resolvedCoords[beer.id] ?: (if (beer.latitude != null && beer.longitude != null) Pair(beer.latitude, beer.longitude) else null)
                                        if (coords != null) {
                                            webViewRef?.evaluateJavascript(
                                                "if (window.focusBeerLocation) { window.focusBeerLocation(${coords.first}, ${coords.second}, '${beer.id}', 15); }",
                                                null
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobeBeerCarouselItem(
    beer: SavedBeerItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val verdictColor = remember(beer.beerVerdict) { getVerdictComposeColor(beer.beerVerdict) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFF1E293B) else Color(0xCC0F172A),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, verdictColor) else androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x40334155)),
                contentAlignment = Alignment.Center
            ) {
                val hasLocalFile = !beer.imagePath.isNullOrBlank() && File(beer.imagePath).exists()
                val imageBase64Bytes = if (!hasLocalFile && !beer.imageBase64.isNullOrBlank()) {
                    remember(beer.imageBase64) { ImageUtils.base64ToByteArray(beer.imageBase64) }
                } else null

                if (hasLocalFile) {
                    AsyncImage(
                        model = File(beer.imagePath!!),
                        contentDescription = beer.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (imageBase64Bytes != null) {
                    AsyncImage(
                        model = imageBase64Bytes,
                        contentDescription = beer.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = beer.beerVerdict.emoji.ifEmpty { "🍺" }, fontSize = 20.sp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = beer.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = beer.origin ?: beer.brandOrProducer.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GlobeSelectedBeerCard(
    beer: SavedBeerItem,
    onClose: () -> Unit,
    onOpenDetails: () -> Unit,
    onZoomIn: () -> Unit,
    onNavigateMaps: () -> Unit
) {
    val verdictColor = remember(beer.beerVerdict) { getVerdictComposeColor(beer.beerVerdict) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF20F172A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, verdictColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = verdictColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Verdict Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = verdictColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, verdictColor.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "${beer.beerVerdict.emoji} ${beer.beerVerdict.title}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Beer Photo Thumbnail
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    val hasLocalFile = !beer.imagePath.isNullOrBlank() && File(beer.imagePath).exists()
                    val imageBase64Bytes = if (!hasLocalFile && !beer.imageBase64.isNullOrBlank()) {
                        remember(beer.imageBase64) { ImageUtils.base64ToByteArray(beer.imageBase64) }
                    } else null

                    if (hasLocalFile) {
                        AsyncImage(
                            model = File(beer.imagePath!!),
                            contentDescription = beer.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (imageBase64Bytes != null) {
                        AsyncImage(
                            model = imageBase64Bytes,
                            contentDescription = beer.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = beer.beerVerdict.emoji.ifEmpty { "🍺" }, fontSize = 28.sp)
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = beer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!beer.brandOrProducer.isNullOrBlank()) {
                        Text(
                            text = beer.brandOrProducer,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB300),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!beer.origin.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Text(
                                text = beer.origin,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Rating Stars
                    if (beer.rating > 0f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = if (beer.rating >= star) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = null,
                                    tint = if (beer.rating >= star) Color(0xFFFFB300) else Color(0x4094A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onZoomIn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Detail-Zoom", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = onOpenDetails,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB300),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bier-Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onNavigateMaps,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF38BDF8)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigieren", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

private class GlobeAppBridge(
    private val onMarkerSelected: (String) -> Unit
) {
    @JavascriptInterface
    fun onBeerSelected(beerId: String) {
        onMarkerSelected(beerId)
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun MapLibreGlobeWebView(
    htmlContent: String,
    markersJson: String,
    onWebViewReady: (WebView) -> Unit,
    onBeerTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadsImagesAutomatically = true
                setBackgroundColor(0xFF080C14.toInt())

                addJavascriptInterface(GlobeAppBridge { beerId ->
                    post { onBeerTapped(beerId) }
                }, "AndroidBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        Log.i("MapLibreGlobe", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("if (window.updatePoints) { window.updatePoints(${JSONObject.quote(markersJson)}); }", null)
                    }
                }

                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

                loadDataWithBaseURL("https://unpkg.com/", htmlContent, "text/html", "UTF-8", null)
                onWebViewReady(this)
            }
        },
        update = { webView ->
            onWebViewReady(webView)
        }
    )
}

private fun generateMapLibreGlobeHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link href="https://unpkg.com/maplibre-gl@5.0.0/dist/maplibre-gl.css" rel="stylesheet" />
            <script src="https://unpkg.com/maplibre-gl@5.0.0/dist/maplibre-gl.js"></script>
            <script>
                if (typeof maplibregl === 'undefined') {
                    document.write('<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/maplibre-gl@5.0.0/dist/maplibre-gl.css" />');
                    document.write('<script src="https://cdn.jsdelivr.net/npm/maplibre-gl@5.0.0/dist/maplibre-gl.js"><\/script>');
                }
            </script>
            <style>
                * { box-sizing: border-box; }
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    min-height: 100%;
                    background: #080C14;
                    overflow: hidden;
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                }
                #map {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100vw;
                    height: 100vh;
                    background: #080C14;
                    overflow: hidden;
                }

                /* Fixed screen-space Pin on Globe */
                .map-pin {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    transform: translate(-50%, -100%);
                    user-select: none;
                    -webkit-user-select: none;
                    cursor: pointer;
                    filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.7));
                }

                /* Hide pins when on back side of the 3D globe */
                .maplibregl-marker.maplibregl-marker-covered {
                    display: none !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                }

                .pin-beacon {
                    width: 36px;
                    height: 36px;
                    border-radius: 50%;
                    background: #0f172a;
                    border: 2.5px solid #ffb300;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 18px;
                    transition: transform 0.2s ease, box-shadow 0.2s ease;
                    z-index: 2;
                }

                .map-pin.selected .pin-beacon {
                    transform: scale(1.22);
                    box-shadow: 0 0 16px #ffb300 !important;
                }

                .pin-pulse {
                    position: absolute;
                    top: 18px;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    width: 36px;
                    height: 36px;
                    border-radius: 50%;
                    animation: radarPulse 2.4s infinite ease-out;
                    pointer-events: none;
                    z-index: 1;
                }

                @keyframes radarPulse {
                    0% { transform: translate(-50%, -50%) scale(0.9); opacity: 0.8; }
                    100% { transform: translate(-50%, -50%) scale(2.4); opacity: 0; }
                }

                .pin-title {
                    margin-top: 3px;
                    background: rgba(15, 23, 42, 0.92);
                    color: #ffffff;
                    font-size: 11px;
                    font-weight: 700;
                    padding: 2px 7px;
                    border-radius: 6px;
                    border: 1px solid rgba(255, 255, 255, 0.25);
                    white-space: nowrap;
                    max-width: 130px;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    pointer-events: none;
                    z-index: 3;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = null;
                var markers = [];
                var rawBeerPoints = [];
                var isAutoRotating = true;
                var isUserInteracting = false;
                var currentActiveStyle = 'satellite';
                var lastRotateTime = 0;

                function resizeMap() {
                    var mapEl = document.getElementById('map');
                    if (mapEl) {
                        mapEl.style.width = window.innerWidth + 'px';
                        mapEl.style.height = window.innerHeight + 'px';
                    }
                    if (map) {
                        map.resize();
                    }
                }
                window.addEventListener('resize', resizeMap);

                function initGlobeMap() {
                    resizeMap();
                    if (typeof maplibregl === 'undefined') {
                        setTimeout(initGlobeMap, 100);
                        return;
                    }
                    if (map) return;

                    try {
                        var tileSources = {
                            'satellite': {
                                type: 'raster',
                                tiles: [
                                    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'
                                ],
                                tileSize: 256,
                                maxzoom: 19,
                                attribution: '&copy; Esri, Maxar'
                            },
                            'osm': {
                                type: 'raster',
                                tiles: [
                                    'https://tile.openstreetmap.de/{z}/{x}/{y}.png'
                                ],
                                tileSize: 256,
                                maxzoom: 19,
                                attribution: '&copy; OpenStreetMap DE'
                            },
                            'dark': {
                                type: 'raster',
                                tiles: [
                                    'https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png'
                                ],
                                tileSize: 256,
                                maxzoom: 19,
                                attribution: '&copy; CARTO'
                            }
                        };

                        map = new maplibregl.Map({
                            container: 'map',
                            style: {
                                version: 8,
                                projection: { type: 'globe' },
                                sources: tileSources,
                                layers: [
                                    {
                                        id: 'layer-satellite',
                                        type: 'raster',
                                        source: 'satellite',
                                        layout: { visibility: 'visible' }
                                    },
                                    {
                                        id: 'layer-osm',
                                        type: 'raster',
                                        source: 'osm',
                                        layout: { visibility: 'none' }
                                    },
                                    {
                                        id: 'layer-dark',
                                        type: 'raster',
                                        source: 'dark',
                                        layout: { visibility: 'none' }
                                    }
                                ],
                                sky: {
                                    'sky-color': '#080C14',
                                    'horizon-color': '#ffb300',
                                    'fog-color': '#080C14'
                                }
                            },
                            center: [10.5, 51.0],
                            zoom: 2.2,
                            pitch: 25,
                            minZoom: 0,
                            maxZoom: 19,
                            attributionControl: false
                        });

                        map.on('mousedown', function() { isUserInteracting = true; });
                        map.on('touchstart', function() { isUserInteracting = true; });
                        map.on('dragstart', function() { isUserInteracting = true; });
                        map.on('zoomstart', function() { isUserInteracting = true; });
                        map.on('mouseup', function() { setTimeout(function() { isUserInteracting = false; }, 2500); });
                        map.on('touchend', function() { setTimeout(function() { isUserInteracting = false; }, 2500); });

                        // Smooth 60fps auto-rotation loop using requestAnimationFrame without buffer fencing errors
                        function autoRotateLoop(timestamp) {
                            if (!lastRotateTime) lastRotateTime = timestamp;
                            var delta = timestamp - lastRotateTime;
                            lastRotateTime = timestamp;

                            if (isAutoRotating && !isUserInteracting && map) {
                                try {
                                    var proj = map.getProjection();
                                    if (proj && proj.type === 'globe') {
                                        var center = map.getCenter();
                                        var newLng = center.lng - (delta * 0.0035);
                                        if (newLng < -180) newLng += 360;
                                        map.setCenter([newLng, center.lat]);
                                    }
                                } catch (_) {}
                            }
                            requestAnimationFrame(autoRotateLoop);
                        }
                        requestAnimationFrame(autoRotateLoop);

                        map.on('load', function() {
                            resizeMap();
                            renderMarkers();
                            setTimeout(resizeMap, 200);
                        });

                    } catch (e) {
                        console.error("Globe map init error: " + e);
                    }
                }

                function renderMarkers() {
                    if (!map) return;

                    // Clear old markers
                    markers.forEach(function(m) { m.remove(); });
                    markers = [];

                    rawBeerPoints.forEach(function(beer) {
                        if (beer.lat === undefined || beer.lng === undefined) return;

                        var color = beer.color || '#FFB300';
                        var emoji = beer.verdictEmoji || '🍺';
                        var name = beer.name || 'Bier';

                        var el = document.createElement('div');
                        el.className = 'map-pin';
                        el.setAttribute('data-id', beer.id);

                        el.innerHTML =
                            '<div class="pin-beacon" style="border-color: ' + color + '; box-shadow: 0 0 12px ' + color + ';">' +
                                '<span>' + emoji + '</span>' +
                            '</div>' +
                            '<div class="pin-pulse" style="background: ' + color + ';"></div>' +
                            '<div class="pin-title">' + name + '</div>';

                        el.addEventListener('click', function(e) {
                            e.stopPropagation();
                            isAutoRotating = false;
                            highlightPin(beer.id);
                            if (window.AndroidBridge && window.AndroidBridge.onBeerSelected) {
                                window.AndroidBridge.onBeerSelected(beer.id);
                            }
                            map.flyTo({
                                center: [beer.lng, beer.lat],
                                zoom: Math.max(map.getZoom(), 15),
                                pitch: 45,
                                duration: 1600,
                                essential: true
                            });
                        });

                        var marker = new maplibregl.Marker({ element: el, anchor: 'bottom' })
                            .setLngLat([beer.lng, beer.lat])
                            .addTo(map);

                        markers.push(marker);
                    });
                }

                function highlightPin(beerId) {
                    var allPins = document.querySelectorAll('.map-pin');
                    allPins.forEach(function(p) {
                        if (p.getAttribute('data-id') === beerId) {
                            p.classList.add('selected');
                        } else {
                            p.classList.remove('selected');
                        }
                    });
                }

                window.unhighlightMarkers = function() {
                    var allPins = document.querySelectorAll('.map-pin');
                    allPins.forEach(function(p) {
                        p.classList.remove('selected');
                    });
                };

                window.updatePoints = function(pointsJson) {
                    try {
                        var parsed = typeof pointsJson === 'string' ? JSON.parse(pointsJson) : pointsJson;
                        rawBeerPoints = parsed || [];
                        renderMarkers();
                    } catch (e) {
                        console.error("Update points error: " + e);
                    }
                };

                window.focusBeerLocation = function(lat, lng, beerId, targetZoom) {
                    if (!map) return;
                    isAutoRotating = false;
                    if (beerId) highlightPin(beerId);
                    var z = targetZoom || 15;
                    map.flyTo({
                        center: [lng, lat],
                        zoom: z,
                        pitch: 45,
                        duration: 1600,
                        essential: true
                    });
                };

                window.resetView = function() {
                    if (!map) return;
                    window.unhighlightMarkers();
                    isAutoRotating = true;
                    map.flyTo({
                        center: [10.5, 51.0],
                        zoom: 2.2,
                        pitch: 25,
                        bearing: 0,
                        duration: 1200,
                        essential: true
                    });
                };

                window.setAutoRotation = function(enabled) {
                    isAutoRotating = enabled;
                };

                window.setMapStyle = function(styleKey) {
                    if (!map) return;
                    currentActiveStyle = styleKey;
                    ['satellite', 'osm', 'dark'].forEach(function(s) {
                        var layerId = 'layer-' + s;
                        if (map.getLayer(layerId)) {
                            map.setLayoutProperty(layerId, 'visibility', s === styleKey ? 'visible' : 'none');
                        }
                    });
                };

                window.setProjection = function(projKey) {
                    if (!map) return;
                    try {
                        map.setProjection({ type: projKey });
                    } catch (e) {
                        console.error("Set projection error: " + e);
                    }
                };

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initGlobeMap);
                } else {
                    initGlobeMap();
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
