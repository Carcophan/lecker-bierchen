package com.picscan.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.picscan.app.data.model.BeerListType
import com.picscan.app.data.model.BeerVerdict
import com.picscan.app.data.model.SavedBeerItem
import com.picscan.app.ui.components.DrinkCategoryBadge
import com.picscan.app.ui.viewmodel.ScannerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BeerSortOption(val title: String) {
    NEWEST("Neueste zuerst"),
    HIGHEST_RATED("Beste Bewertung"),
    NAME_ASC("Name (A–Z)"),
    VERDICT_TIER("Tier-Rang (1–5)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeerCollectionScreen(
    viewModel: ScannerViewModel,
    initialTab: BeerListType = BeerListType.KNOWN,
    onNavigateBack: () -> Unit,
    onBeerSelected: (SavedBeerItem) -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedVerdictFilter by remember { mutableStateOf<BeerVerdict?>(null) }
    var selectedSortOption by remember { mutableStateOf(BeerSortOption.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    val knownBeers by viewModel.knownBeers.collectAsState()
    val wishlistBeers by viewModel.wishlistBeers.collectAsState()

    val currentList = if (selectedTab == BeerListType.KNOWN) knownBeers else wishlistBeers

    // Dialog state for editing rating and notes
    var beerToEdit by remember { mutableStateOf<SavedBeerItem?>(null) }
    var beerToDelete by remember { mutableStateOf<SavedBeerItem?>(null) }

    val filteredList = remember(currentList, searchQuery, selectedVerdictFilter, selectedSortOption) {
        var result = currentList

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter {
                it.name.lowercase().contains(q) ||
                (it.brandOrProducer?.lowercase()?.contains(q) == true) ||
                (it.origin?.lowercase()?.contains(q) == true) ||
                it.beerVerdict.title.lowercase().contains(q) ||
                it.userNotes.lowercase().contains(q)
            }
        }

        if (selectedVerdictFilter != null) {
            result = result.filter { it.beerVerdict == selectedVerdictFilter }
        }

        when (selectedSortOption) {
            BeerSortOption.NEWEST -> result.sortedByDescending { it.timestamp }
            BeerSortOption.HIGHEST_RATED -> result.sortedByDescending { it.rating }
            BeerSortOption.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            BeerSortOption.VERDICT_TIER -> result.sortedBy {
                if (it.beerVerdict.tierNumber == 0) 99 else it.beerVerdict.tierNumber
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meine Biere (Firebase)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sortieren")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            BeerSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.title,
                                            fontWeight = if (selectedSortOption == option) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedSortOption = option
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (selectedSortOption == option) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selector: "Kenne ich" vs "Will ich"
            PrimaryTabRow(
                selectedTabIndex = if (selectedTab == BeerListType.KNOWN) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == BeerListType.KNOWN,
                    onClick = { selectedTab = BeerListType.KNOWN },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🍺 Kenne ich", fontWeight = FontWeight.Bold)
                            Badge(
                                containerColor = if (selectedTab == BeerListType.KNOWN)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text("${knownBeers.size}")
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTab == BeerListType.WISHLIST,
                    onClick = { selectedTab = BeerListType.WISHLIST },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📌 Will ich", fontWeight = FontWeight.Bold)
                            Badge(
                                containerColor = if (selectedTab == BeerListType.WISHLIST)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text("${wishlistBeers.size}")
                            }
                        }
                    }
                )
            }

            // Search Bar & Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            if (selectedTab == BeerListType.KNOWN)
                                "Probierte Biere durchsuchen…"
                            else
                                "Wunschliste durchsuchen…"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Löschen")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                // Verdict Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedVerdictFilter == null,
                            onClick = { selectedVerdictFilter = null },
                            label = { Text("Alle (${currentList.size})") }
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
                        val count = currentList.count { it.beerVerdict == verdict }
                        if (count > 0 || selectedVerdictFilter == verdict) {
                            FilterChip(
                                selected = selectedVerdictFilter == verdict,
                                onClick = {
                                    selectedVerdictFilter = if (selectedVerdictFilter == verdict) null else verdict
                                },
                                label = { Text("${verdict.emoji} ${verdict.title} ($count)") }
                            )
                        }
                    }
                }
            }

            // Content List or Empty State
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selectedTab == BeerListType.KNOWN) "🍺" else "📌",
                            fontSize = 48.sp
                        )
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedVerdictFilter != null)
                                "Keine passenden Biere gefunden"
                            else if (selectedTab == BeerListType.KNOWN)
                                "Noch keine Biere als 'Kenne ich' markiert"
                            else
                                "Deine 'Will ich'-Wunschliste ist noch leer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedVerdictFilter != null)
                                "Passe deinen Suchbegriff oder Filter an."
                            else if (selectedTab == BeerListType.KNOWN)
                                "Scanne ein Bier mit der Kamera und speichere es mit 'Kenne ich' in deiner Firebase Cloud Datenbank."
                            else
                                "Finde spannende Biere beim Scannen und tippe auf 'Will ich', um sie für später zu merken.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    items(filteredList, key = { it.id }) { beer ->
                        SavedBeerCard(
                            beer = beer,
                            onCardClick = {
                                viewModel.selectSavedBeer(beer)
                                onBeerSelected(beer)
                            },
                            onToggleStatus = {
                                val targetType = if (beer.listType == BeerListType.KNOWN)
                                    BeerListType.WISHLIST
                                else
                                    BeerListType.KNOWN
                                viewModel.updateBeerStatus(beer.id, targetType)
                            },
                            onEditNotes = {
                                beerToEdit = beer
                            },
                            onDelete = {
                                beerToDelete = beer
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Rating and Notes Dialog
    if (beerToEdit != null) {
        val beer = beerToEdit!!
        var editRating by remember(beer) { mutableStateOf(beer.rating) }
        var editNotes by remember(beer) { mutableStateOf(beer.userNotes) }

        AlertDialog(
            onDismissRequest = { beerToEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(beer.beerVerdict.emoji.ifEmpty { "🍺" })
                    Text(beer.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Persönliche Bewertung:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    // 5-Star Interactive Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { starIndex ->
                            IconButton(onClick = { editRating = starIndex.toFloat() }) {
                                Icon(
                                    imageVector = if (editRating >= starIndex) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$starIndex Sterne",
                                    tint = if (editRating >= starIndex) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        label = { Text("Verkostungsnotiz / Kommentar") },
                        placeholder = { Text("z.B. Frisch vom Fass, sehr malzig, perfekt zum Grillen…") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBeerRatingAndNotes(beer.id, editRating, editNotes)
                        beerToEdit = null
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { beerToEdit = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (beerToDelete != null) {
        val beer = beerToDelete!!
        AlertDialog(
            onDismissRequest = { beerToDelete = null },
            title = { Text("Bier aus Liste löschen?") },
            text = { Text("Möchtest du '${beer.name}' wirklich aus deiner Firebase-Datenbank entfernen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBeerFromList(beer.id)
                        beerToDelete = null
                    }
                ) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { beerToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

private fun getVerdictBadgeColors(verdict: BeerVerdict): Pair<Color, Color> {
    return when (verdict) {
        BeerVerdict.HOPFENBOMBE -> Pair(Color(0xFF00E676), Color(0xFF052B14))
        BeerVerdict.LECKER_BIERCHEN -> Pair(Color(0xFFFFB300), Color(0xFF2E1C00))
        BeerVerdict.WEGBIER -> Pair(Color(0xFF00B0FF), Color(0xFF002233))
        BeerVerdict.PENNERGLUECK -> Pair(Color(0xFFFFAB00), Color(0xFF2B1F17))
        BeerVerdict.PISSBRUEHE -> Pair(Color(0xFFFF1744), Color(0xFFFFFFFF))
        BeerVerdict.NONE -> Pair(Color.Gray, Color.White)
    }
}

@Composable
fun SavedBeerCard(
    beer: SavedBeerItem,
    onCardClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onEditNotes: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN) }
    val formattedDate = remember(beer.timestamp) { dateFormat.format(Date(beer.timestamp)) }
    val (badgeBg, badgeText) = remember(beer.beerVerdict) { getVerdictBadgeColors(beer.beerVerdict) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Thumbnail
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!beer.imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(beer.imagePath),
                        contentDescription = beer.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = beer.beerVerdict.emoji.ifEmpty { "🍺" },
                        fontSize = 28.sp
                    )
                }
            }

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tier Badge & Category
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (beer.beerVerdict != BeerVerdict.NONE) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBg.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "${beer.beerVerdict.emoji} ${beer.beerVerdict.title}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (!beer.abvOrCaffeine.isNullOrBlank()) {
                        Text(
                            text = beer.abvOrCaffeine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Name
                Text(
                    text = beer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Producer / Brand & Origin
                if (!beer.brandOrProducer.isNullOrBlank() || !beer.origin.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(beer.brandOrProducer, beer.origin).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Star Rating & User Notes Preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (beer.rating > 0f) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (beer.rating >= star) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = if (beer.rating >= star) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (beer.userNotes.isNotBlank()) {
                        Text(
                            text = "„${beer.userNotes}“",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Move button (e.g. from Wishlist to Known, or vice versa)
                IconButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (beer.listType == BeerListType.KNOWN) Icons.Default.BookmarkBorder else Icons.Default.CheckCircleOutline,
                        contentDescription = if (beer.listType == BeerListType.KNOWN) "Zu 'Will ich' verschieben" else "Als 'Kenne ich' markieren",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Edit rating / notes
                IconButton(
                    onClick = onEditNotes,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = "Bewertung & Notiz bearbeiten",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
