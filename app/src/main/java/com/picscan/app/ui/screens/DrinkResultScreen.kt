package com.picscan.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.picscan.app.data.model.BeerListType
import com.picscan.app.data.model.BeerVerdict
import com.picscan.app.data.model.SavedBeerItem
import com.picscan.app.ui.components.BeerVerdictCard
import com.picscan.app.ui.components.BeerVerdictCelebrationDialog
import com.picscan.app.ui.components.DrinkCategoryBadge
import com.picscan.app.ui.components.InfoChip
import com.picscan.app.ui.components.LevelIndicator
import com.picscan.app.ui.components.TagChip
import com.picscan.app.ui.viewmodel.ScannerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkResultScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBeers: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val allSavedBeers by viewModel.allSavedBeers.collectAsState()
    val drink = uiState.currentDrink

    if (drink == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Keine Getränkedaten verfügbar", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("Zurück")
                }
            }
        }
        return
    }

    val scrollState = rememberScrollState()
    val beerVerdict = remember(drink) { drink.resolveBeerVerdict() }
    var showBeerAlert by remember(drink) { mutableStateOf(beerVerdict != BeerVerdict.NONE) }

    // Check if this beer is already in the Firebase database
    val savedBeer = remember(allSavedBeers, drink.name) {
        allSavedBeers.find { it.name.equals(drink.name, ignoreCase = true) }
    }

    var showEditNoteDialog by remember { mutableStateOf(false) }

    // Display animated modal overlay on scan
    if (showBeerAlert && beerVerdict != BeerVerdict.NONE) {
        BeerVerdictCelebrationDialog(
            drink = drink,
            onDismiss = { showBeerAlert = false }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccessMessage) {
        uiState.saveSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearSaveSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Getränkeprofil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBeers) {
                        Icon(Icons.Default.SportsBar, contentDescription = "Meine Biere")
                    }
                    IconButton(onClick = {
                        val shareText = buildString {
                            appendLine("🍹 ${drink.name}")
                            if (beerVerdict != BeerVerdict.NONE) {
                                appendLine("Bier-Bewertung: ${beerVerdict.emoji} ${beerVerdict.title}")
                            }
                            if (!drink.brandOrProducer.isNullOrBlank()) appendLine("Marke / Brauerei: ${drink.brandOrProducer}")
                            if (!drink.abvOrCaffeine.isNullOrBlank()) appendLine("Alkohol / Koffein: ${drink.abvOrCaffeine}")
                            appendLine("\n${drink.description}")
                            if (drink.flavorProfile.tastingNotes.isNotEmpty()) {
                                appendLine("\nGeschmacksnoten: ${drink.flavorProfile.tastingNotes.joinToString(", ")}")
                            }
                            if (drink.servingRecommendations.foodPairings.isNotEmpty()) {
                                appendLine("\nSpeisenbegleiter: ${drink.servingRecommendations.foodPairings.joinToString(", ")}")
                            }
                            appendLine("\nGescannt mit lecker Bierchen! AI")
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Getränke-Info teilen"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Teilen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToBeers,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.SportsBar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Meine Biere", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Neuer Scan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drink Photo Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.currentImageBitmap != null) {
                    Image(
                        bitmap = uiState.currentImageBitmap!!.asImageBitmap(),
                        contentDescription = "Getränkefoto",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (uiState.currentImagePath != null) {
                    AsyncImage(
                        model = File(uiState.currentImagePath!!),
                        contentDescription = "Getränkefoto",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.LocalDrink,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Beer Verdict Feature Banner (5-Tier Beer Ranking)
            if (beerVerdict != BeerVerdict.NONE) {
                BeerVerdictCard(
                    drink = drink,
                    onShowFullAlert = { showBeerAlert = true }
                )
            }

            // FIREBASE BEER SAVING CARD ("Kenne ich" vs "Will ich")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ),
                    width = 1.5.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Firebase Cloud-Speicher",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (savedBeer != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (savedBeer.listType == BeerListType.KNOWN) "✓ In 'Kenne ich'" else "✓ In 'Will ich'",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (savedBeer == null)
                            "Speichere dieses Bier direkt in deiner persönlichen Firebase-Datenbank:"
                        else
                            "Dieses Bier ist in deiner Firebase-Sammlung gespeichert. Status oder Notizen anpassen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Two Primary Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // "Kenne ich" Button
                        val isKnownSelected = savedBeer?.listType == BeerListType.KNOWN
                        Button(
                            onClick = {
                                viewModel.saveBeerToList(drink, BeerListType.KNOWN)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = if (isKnownSelected) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("🍺", fontSize = 16.sp)
                                    Text(
                                        text = "Kenne ich",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Text(
                                    text = "Schon probiert",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // "Will ich" Button
                        val isWishlistSelected = savedBeer?.listType == BeerListType.WISHLIST
                        Button(
                            onClick = {
                                viewModel.saveBeerToList(drink, BeerListType.WISHLIST)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = if (isWishlistSelected) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0277BD),
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("📌", fontSize = 16.sp)
                                    Text(
                                        text = "Will ich",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Text(
                                    text = "Wunschliste",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // If saved, show rating & notes editor
                    if (savedBeer != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Deine Bewertung & Notiz:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    TextButton(
                                        onClick = { showEditNoteDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bearbeiten")
                                    }
                                }

                                // Interactive Quick Star Rating
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    (1..5).forEach { star ->
                                        IconButton(
                                            onClick = {
                                                viewModel.updateBeerRatingAndNotes(
                                                    savedBeer.id,
                                                    if (savedBeer.rating == star.toFloat()) 0f else star.toFloat(),
                                                    savedBeer.userNotes
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (savedBeer.rating >= star) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                contentDescription = "$star Sterne",
                                                tint = if (savedBeer.rating >= star) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    if (savedBeer.rating > 0f) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${savedBeer.rating.toInt()}/5 Sterne",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (savedBeer.userNotes.isNotBlank()) {
                                    Text(
                                        text = "„${savedBeer.userNotes}“",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Header info (Category, Name, Brand, Origin)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DrinkCategoryBadge(category = drink.category)

                Text(
                    text = drink.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!drink.brandOrProducer.isNullOrBlank() || !drink.origin.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(drink.brandOrProducer, drink.origin).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Info Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!drink.abvOrCaffeine.isNullOrBlank()) {
                    InfoChip(
                        icon = Icons.Default.LocalBar,
                        title = "Stärke",
                        value = drink.abvOrCaffeine,
                        modifier = Modifier.weight(1f)
                    )
                }

                InfoChip(
                    icon = Icons.Default.Speed,
                    title = "Körper",
                    value = drink.flavorProfile.body,
                    modifier = Modifier.weight(1f)
                )
            }

            // Description Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Sommelier-Übersicht",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = drink.description,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }

            // Flavor & Tasting Profile Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Geschmacksprofil",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Level Bars
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LevelIndicator(label = "Süße", level = drink.flavorProfile.sweetnessLevel)
                        LevelIndicator(label = "Bitterkeit", level = drink.flavorProfile.bitternessLevel)
                        LevelIndicator(label = "Säure", level = drink.flavorProfile.acidityLevel)
                    }

                    // Aromas
                    if (drink.flavorProfile.aromas.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Aroma & Bouquet",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                drink.flavorProfile.aromas.forEach { aroma ->
                                    TagChip(text = aroma)
                                }
                            }
                        }
                    }

                    // Tasting Notes
                    if (drink.flavorProfile.tastingNotes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Geschmacksnoten",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                drink.flavorProfile.tastingNotes.forEach { note ->
                                    TagChip(text = note)
                                }
                            }
                        }
                    }
                }
            }

            // Serving & Pairing Guide Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Servier- & Speiseempfehlungen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoChip(
                            icon = Icons.Default.Thermostat,
                            title = "Ideale Temp.",
                            value = drink.servingRecommendations.idealTemperature,
                            modifier = Modifier.weight(1f)
                        )
                        InfoChip(
                            icon = Icons.Default.WineBar,
                            title = "Glas",
                            value = drink.servingRecommendations.glassware,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (drink.servingRecommendations.foodPairings.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Empfohlene Speisenbegleiter:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            drink.servingRecommendations.foodPairings.forEach { pairing ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = pairing,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    if (!drink.servingRecommendations.mixologyTipOrCocktail.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Liquor,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = drink.servingRecommendations.mixologyTipOrCocktail,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Nutrition Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Geschätzte Nährwerte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Kalorien", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(drink.nutrition.estimatedCalories, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Zucker", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(drink.nutrition.estimatedSugar, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Kohlenhydrate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(drink.nutrition.estimatedCarbs, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (drink.nutrition.dietaryHighlights.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            drink.nutrition.dietaryHighlights.forEach { tag ->
                                TagChip(text = tag)
                            }
                        }
                    }
                }
            }

            // Interesting Facts Card
            if (drink.interestingFacts.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Schon gewusst?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        drink.interestingFacts.forEach { fact ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = fact,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Edit Note & Rating Dialog
    if (showEditNoteDialog && savedBeer != null) {
        var editRating by remember(savedBeer) { mutableStateOf(savedBeer.rating) }
        var editNotes by remember(savedBeer) { mutableStateOf(savedBeer.userNotes) }

        AlertDialog(
            onDismissRequest = { showEditNoteDialog = false },
            title = { Text("Bewertung & Verkostungsnotiz", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Sterne-Bewertung:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

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
                        label = { Text("Persönliche Notiz") },
                        placeholder = { Text("Wo getrunken, Eindruck, Geschmack…") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBeerRatingAndNotes(savedBeer.id, editRating, editNotes)
                        showEditNoteDialog = false
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNoteDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
