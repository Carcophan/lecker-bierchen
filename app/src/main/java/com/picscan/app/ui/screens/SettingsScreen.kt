package com.picscan.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picscan.app.data.repository.ApiKeyPreferenceRepository
import com.picscan.app.data.repository.SyncState
import com.picscan.app.ui.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    val currentApiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val knownBeers by viewModel.knownBeers.collectAsState()
    val wishlistBeers by viewModel.wishlistBeers.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val currentUserId = viewModel.currentUserId

    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var saveFeedbackMessage by remember { mutableStateOf<String?>(null) }

    var customUserIdInput by remember(currentUserId) { mutableStateOf(currentUserId) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var userAuthFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen & Konfiguration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Firebase Cloud Database Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Google Firebase Datenbank",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "Deine Biere werden automatisch in Google Firebase Firestore sowie lokal synchronisiert. So stehen deine 'Kenne ich'- und 'Will ich'-Listen jederzeit zur Verfügung.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    // Sync Status Indicator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (val state = syncState) {
                                    is SyncState.Syncing -> {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = "Synchronisiere mit Firebase...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    is SyncState.Success -> {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Verbunden & synchronisiert (${state.itemCount} Biere)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    is SyncState.Error -> {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Fehler: ${state.message}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Firebase Betriebsbereit",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.refreshFirebaseSync() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Manuell synchronisieren",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (currentUserId.isNotBlank()) {
                        Text(
                            text = "Firebase Nutzer-ID: $currentUserId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🍺", fontSize = 16.sp)
                                Text("Kenne ich: ${knownBeers.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📌", fontSize = 16.sp)
                                Text("Will ich: ${wishlistBeers.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Firebase User ID & Email Authentication Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Firebase Nutzer-ID & Konto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Gib deine UUID oder Nutzer-ID aus der Firestore-Datenbank ein, um deine Biere direkt zu laden:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customUserIdInput,
                        onValueChange = {
                            customUserIdInput = it
                            userAuthFeedbackMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nutzer-ID / UUID") },
                        singleLine = true,
                        trailingIcon = {
                            if (customUserIdInput.isNotEmpty()) {
                                IconButton(onClick = { customUserIdInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Leeren")
                                }
                            }
                        }
                    )

                    Button(
                        onClick = {
                            if (customUserIdInput.isNotBlank()) {
                                viewModel.setCustomUserId(customUserIdInput)
                                focusManager.clearFocus()
                                userAuthFeedbackMessage = "Nutzer-ID / UUID gesetzt & Synchronisation gestartet!"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nutzer-ID / UUID festlegen & synchronisieren")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Oder melde dich mit deiner E-Mail-Adresse & Passwort bei Firebase Auth an:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            userAuthFeedbackMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-Mail-Adresse") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            userAuthFeedbackMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Passwort anzeigen"
                                )
                            }
                        }
                    )

                    Button(
                        onClick = {
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                viewModel.signInWithEmailAndPassword(emailInput, passwordInput) { res ->
                                    res.onSuccess { uid ->
                                        userAuthFeedbackMessage = "Erfolgreich als $emailInput angemeldet! (UID: $uid)"
                                    }.onFailure { err ->
                                        userAuthFeedbackMessage = "Anmeldung fehlgeschlagen: ${err.localizedMessage}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mit E-Mail anmelden")
                    }

                    if (userAuthFeedbackMessage != null) {
                        Text(
                            text = userAuthFeedbackMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Google AI Pro & API Key Explanation Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Google AI Pro vs. API-Schlüssel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "Endkunden-Abonnements (Google AI Pro / Google One AI Premium) bieten Zugriff auf Gemini Advanced in den normalen Apps, beinhalten jedoch keine Entwickler-API-Schlüssel für Drittanbieter-Apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "Um Getränke in lecker Bierchen! zu scannen, kannst du dir in wenigen Sekunden einen KOSTENLOSEN Gemini-API-Schlüssel im Google AI Studio erstellen. Das Kontingent ist für private Scans völlig ausreichend!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kostenlosen API-Schlüssel im Google AI Studio holen")
                    }
                }
            }

            // API Key Input Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Gemini-API-Schlüssel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            saveFeedbackMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API-Schlüssel (z. B. AIzaSy...)") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Sichtbarkeit umschalten"
                                    )
                                }
                                if (apiKeyInput.isNotEmpty()) {
                                    IconButton(onClick = { apiKeyInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Leeren")
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipData = clipboardManager.getText()
                                if (!clipData.isNullOrBlank()) {
                                    apiKeyInput = clipData.text.trim()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Einfügen")
                        }

                        Button(
                            onClick = {
                                viewModel.saveApiKey(apiKeyInput)
                                focusManager.clearFocus()
                                saveFeedbackMessage = "API-Schlüssel erfolgreich gespeichert!"
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Speichern")
                        }
                    }

                    if (saveFeedbackMessage != null) {
                        Text(
                            text = saveFeedbackMessage!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Gemini Model Selector Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "AI-Modell-Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Wähle das Gemini-Modell für deine Getränke-Scans aus:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ApiKeyPreferenceRepository.AVAILABLE_MODELS.forEach { (modelId, modelLabel) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectModel(modelId) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedModel == modelId),
                                onClick = { viewModel.selectModel(modelId) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedModel == modelId) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // App About & Version Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("lecker Bierchen! – AI Getränke- & Bier-Scanner", fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0 (Entwickelt für Android 17 / API 35+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Unterstützt von Google Firebase Firestore, Google Gemini Vision & CameraX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
