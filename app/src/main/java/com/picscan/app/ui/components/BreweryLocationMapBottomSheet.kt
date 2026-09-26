package com.picscan.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.picscan.app.data.service.BreweryLocation
import com.picscan.app.data.service.BreweryLocationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreweryLocationMapBottomSheet(
    beerName: String,
    brandOrProducer: String?,
    origin: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var locationState by remember { mutableStateOf<BreweryLocation?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(origin, brandOrProducer) {
        isLoading = true
        hasError = false
        val loc = BreweryLocationService.resolveLocation(context, origin, brandOrProducer)
        if (loc != null) {
            locationState = loc
            isLoading = false
        } else {
            hasError = true
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Brewery & Origin Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!brandOrProducer.isNullOrBlank()) brandOrProducer else beerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = origin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = {
                    coroutineScope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen")
                }
            }

            // Map Area / Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Standort wird auf der Weltkarte ermittelt...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    locationState != null -> {
                        val loc = locationState!!
                        InteractiveLeafletMapView(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            beerName = beerName,
                            brandOrProducer = brandOrProducer,
                            origin = origin
                        )
                    }

                    else -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Standort konnte nicht exakt aufgelöst werden",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Du kannst die Adresse direkt in einer externen Karten-App öffnen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Location coordinates & city display chip
            locationState?.let { loc ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "GPS: %.4f° N, %.4f° E", loc.latitude, loc.longitude),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Buttons: Open in Google Maps Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val targetQuery = locationState?.let {
                            "${it.latitude},${it.longitude}(${Uri.encode(brandOrProducer ?: beerName)})"
                        } ?: Uri.encode("$origin $beerName")

                        val mapIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("geo:0,0?q=$targetQuery")
                        }

                        try {
                            context.startActivity(mapIntent)
                        } catch (_: Exception) {
                            // Fallback to browser Google Maps URL
                            val webMapIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://www.google.com/maps/search/?api=1&query=$targetQuery")
                            }
                            context.startActivity(webMapIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "In Google Maps öffnen / Navigieren",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun InteractiveLeafletMapView(
    latitude: Double,
    longitude: Double,
    beerName: String,
    brandOrProducer: String?,
    origin: String,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val safeBeerNameJson = remember(beerName) { JSONObject.quote(beerName) }
    val safeBrandJson = remember(brandOrProducer) { JSONObject.quote(brandOrProducer.orEmpty()) }
    val safeOriginJson = remember(origin) { JSONObject.quote(origin) }

    val htmlContent = remember(latitude, longitude, safeBeerNameJson, safeBrandJson, safeOriginJson) {
        generateLeafletHtml(
            lat = latitude,
            lng = longitude,
            beerNameJson = safeBeerNameJson,
            brandOrProducerJson = safeBrandJson,
            originJson = safeOriginJson
        )
    }

    // Trigger map invalidation across BottomSheet slide-in animation lifecycle
    LaunchedEffect(htmlContent, webViewRef) {
        val animationDelays = longArrayOf(100L, 250L, 450L, 750L, 1100L)
        for (d in animationDelays) {
            delay(d)
            webViewRef?.evaluateJavascript("if (window.fixSize) { window.fixSize(); }", null)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadsImagesAutomatically = true
                settings.userAgentString = "LeckerBierchenAndroidApp/1.0 (contact: github.com/Carcophan/lecker-bierchen) ${settings.userAgentString}"

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.i("LeafletWebView", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("if (window.fixSize) { window.fixSize(); }", null)
                    }
                }

                // Immediately recalculate map dimensions whenever Android view layout changes
                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val w = right - left
                    val h = bottom - top
                    val oldW = oldRight - oldLeft
                    val oldH = oldBottom - oldTop
                    if (w > 0 && h > 0 && (w != oldW || h != oldH)) {
                        post {
                            evaluateJavascript("if (window.fixSize) { window.fixSize(); }", null)
                        }
                    }
                }

                // Intercept touch events so user can freely pan and pinch the map without bottom sheet scrolling
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

                tag = htmlContent
                loadDataWithBaseURL("https://openstreetmap.org/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webViewRef = webView
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL("https://openstreetmap.org/", htmlContent, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun generateLeafletHtml(
    lat: Double,
    lng: Double,
    beerNameJson: String,
    brandOrProducerJson: String,
    originJson: String
): String {
    // Format coordinates explicitly with US locale to guarantee decimal dot
    val formattedLat = String.format(java.util.Locale.US, "%.7f", lat)
    val formattedLng = String.format(java.util.Locale.US, "%.7f", lng)

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
            <script>
                // Fallback to unpkg CDN if cloudflare is blocked or slow
                if (typeof L === 'undefined') {
                    document.write('<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />');
                    document.write('<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"><\/script>');
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
                    background: #f1f5f9;
                    overflow: hidden;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                }
                #map {
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    width: 100%;
                    height: 100%;
                    min-height: 250px;
                    background: #f1f5f9;
                }
                .leaflet-popup-content-wrapper {
                    border-radius: 12px;
                    box-shadow: 0 4px 14px rgba(0,0,0,0.25);
                    padding: 2px;
                }
                .leaflet-popup-content {
                    margin: 10px 14px;
                    line-height: 1.35;
                }
                .popup-title {
                    font-weight: 700;
                    font-size: 14px;
                    color: #1c1917;
                    margin-bottom: 2px;
                }
                .brand {
                    font-size: 12px;
                    font-weight: 600;
                    color: #d97706;
                    margin-bottom: 4px;
                }
                .origin {
                    font-size: 11px;
                    color: #57534e;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var rawBeerName = $beerNameJson;
                var rawBrand = $brandOrProducerJson;
                var rawOrigin = $originJson;
                var leafletMap = null;

                function applyDimensions() {
                    var mapEl = document.getElementById('map');
                    if (mapEl) {
                        var h = window.innerHeight || document.documentElement.clientHeight || 350;
                        var w = window.innerWidth || document.documentElement.clientWidth || 350;
                        if (h > 0) {
                            document.documentElement.style.height = h + 'px';
                            document.body.style.height = h + 'px';
                            mapEl.style.height = h + 'px';
                        }
                        if (w > 0) {
                            mapEl.style.width = w + 'px';
                        }
                    }
                    if (leafletMap) {
                        leafletMap.invalidateSize(true);
                    }
                }

                window.fixSize = function() {
                    applyDimensions();
                };

                function escapeHtml(text) {
                    var div = document.createElement('div');
                    div.textContent = text || '';
                    return div.innerHTML;
                }

                function initMap() {
                    if (typeof L === 'undefined') {
                        setTimeout(initMap, 100);
                        return;
                    }
                    if (leafletMap) return;

                    try {
                        applyDimensions();

                        leafletMap = L.map('map', {
                            zoomControl: true,
                            attributionControl: false
                        }).setView([$formattedLat, $formattedLng], 13);

                        // Primary tile layer: OpenStreetMap.de (free, open, high reliability, localized)
                        var primaryLayer = L.tileLayer('https://tile.openstreetmap.de/{z}/{x}/{y}.png', {
                            maxZoom: 19,
                            attribution: '&copy; OpenStreetMap Deutschland'
                        });
                        primaryLayer.addTo(leafletMap);

                        var marker = L.marker([$formattedLat, $formattedLng]).addTo(leafletMap);
                        
                        var safeTitle = escapeHtml(rawBeerName);
                        var safeBrand = escapeHtml(rawBrand);
                        var safeOrigin = escapeHtml(rawOrigin);

                        var brandHtml = safeBrand ? "<div class='brand'>🍺 " + safeBrand + "</div>" : "";
                        var popupContent = "<div class='popup-title'>" + safeTitle + "</div>" +
                                           brandHtml +
                                           "<div class='origin'>📍 " + safeOrigin + "</div>";
                        
                        marker.bindPopup(popupContent).openPopup();

                        // Observe container resizes natively
                        if (window.ResizeObserver) {
                            try {
                                new ResizeObserver(function() {
                                    applyDimensions();
                                }).observe(document.body);
                            } catch (_) {}
                        }

                        window.addEventListener('resize', applyDimensions);
                        setTimeout(applyDimensions, 100);
                        setTimeout(applyDimensions, 300);
                        setTimeout(applyDimensions, 600);
                        setTimeout(applyDimensions, 1000);
                    } catch (err) {
                        console.error("Map initialization failed", err);
                    }
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initMap);
                } else {
                    initMap();
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
