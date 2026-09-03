package com.picscan.app.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.picscan.app.data.model.BeerListType
import com.picscan.app.data.model.DrinkDetails
import com.picscan.app.data.model.SavedBeerItem
import com.picscan.app.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val itemCount: Int, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val message: String) : SyncState()
}

class FirebaseBeerRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val tag = "FirebaseBeerRepo"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val localCacheFile = File(context.filesDir, "saved_beers_cache.json")
    private val beerImagesDir = File(context.filesDir, "beer_images").apply { mkdirs() }

    private val deletedBeerIds = ConcurrentHashMap.newKeySet<String>().apply {
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("picscan_deleted_beer_ids", null)
        if (saved != null) addAll(saved)
    }

    private fun persistDeletedBeerIds() {
        try {
            val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet("picscan_deleted_beer_ids", deletedBeerIds.toSet()).apply()
        } catch (e: Throwable) {
            Log.w(tag, "Could not persist deletedBeerIds: ${e.localizedMessage}")
        }
    }

    private val collectionDocs = ConcurrentHashMap<String, Map<String, SavedBeerItem>>()
    private val pendingLocalBeerIds = ConcurrentHashMap.newKeySet<String>()
    private var hasReceivedAnySnapshot = false

    private val _beersFlow = MutableStateFlow<List<SavedBeerItem>>(emptyList())
    val allBeersFlow: Flow<List<SavedBeerItem>> = _beersFlow.asStateFlow()

    val knownBeersFlow: Flow<List<SavedBeerItem>> = _beersFlow.map { list ->
        list.filter { it.listType == BeerListType.KNOWN }.sortedByDescending { it.timestamp }
    }

    val wishlistBeersFlow: Flow<List<SavedBeerItem>> = _beersFlow.map { list ->
        list.filter { it.listType == BeerListType.WISHLIST }.sortedByDescending { it.timestamp }
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Syncing)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var currentUserId: String = ""

    val userId: String
        get() = currentUserId

    private val initJob: Job

    init {
        loadLocalCache()
        initJob = coroutineScope.launch {
            initFirebase()
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val availability = GoogleApiAvailability.getInstance()
            val result = availability.isGooglePlayServicesAvailable(context)
            if (result != ConnectionResult.SUCCESS) {
                Log.w(tag, "Google Play Services unavailable. ConnectionResult code: $result")
                false
            } else {
                true
            }
        } catch (e: SecurityException) {
            Log.w(tag, "SecurityException checking Google Play Services availability: ${e.localizedMessage}")
            false
        } catch (e: Throwable) {
            Log.w(tag, "Google Play Services check notice: ${e.localizedMessage}")
            false
        }
    }

    private suspend fun ensureInitialized() {
        if (!initJob.isCompleted) {
            initJob.join()
        }
    }

    private suspend fun initFirebase() {
        _syncState.value = SyncState.Syncing
        try {
            if (currentUserId.isBlank()) {
                currentUserId = getOrCreateStableUserId()
            }

            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }

            if (isGooglePlayServicesAvailable()) {
                try {
                    auth = FirebaseAuth.getInstance()
                } catch (e: SecurityException) {
                    Log.w(tag, "SecurityException initializing FirebaseAuth (GMS broker issue): ${e.localizedMessage}")
                    auth = null
                } catch (e: Throwable) {
                    Log.w(tag, "FirebaseAuth initialization notice: ${e.localizedMessage}")
                    auth = null
                }
            } else {
                Log.i(tag, "Google Play Services not available, using device storage fallback ID")
                auth = null
            }

            try {
                val db = FirebaseFirestore.getInstance()
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    db.firestoreSettings = settings
                } catch (e: Throwable) {
                    Log.d(tag, "Firestore settings notice: ${e.localizedMessage}")
                }
                firestore = db
            } catch (e: Throwable) {
                Log.w(tag, "Firestore initialization notice: ${e.localizedMessage}")
            }

            ensureUserAuthenticated()
            startFirestoreListeners()

            // Push any locally cached beers to Firestore to ensure complete synchronization
            syncAllLocalBeersToFirestore()
        } catch (e: Throwable) {
            Log.e(tag, "Firebase initialization error: ${e.localizedMessage}", e)
            if (currentUserId.isBlank()) {
                currentUserId = getOrCreateStableUserId()
            }
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Firebase Initialisierungsfehler")
        }
    }

    suspend fun forceSync() = withContext(Dispatchers.IO) {
        initFirebase()
    }

    private suspend fun ensureUserAuthenticated() {
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        val customId = prefs.getString("custom_user_id", null)
        if (!customId.isNullOrBlank()) {
            currentUserId = customId
            Log.d(tag, "Using stored custom user ID / UUID: $currentUserId")
            return
        }

        val currentAuth = auth

        if (currentAuth != null && isGooglePlayServicesAvailable()) {
            try {
                val existingUser = currentAuth.currentUser
                if (existingUser != null) {
                    currentUserId = existingUser.uid
                    prefs.edit().putString("firebase_user_id", currentUserId).apply()
                    Log.d(tag, "Authenticated with existing user UID: $currentUserId")
                    return
                }

                val result = currentAuth.signInAnonymously().await()
                val uid = result.user?.uid
                if (!uid.isNullOrBlank()) {
                    currentUserId = uid
                    prefs.edit().putString("firebase_user_id", currentUserId).apply()
                    Log.d(tag, "Signed in anonymously with UID: $currentUserId")
                    return
                }
            } catch (e: SecurityException) {
                Log.w(tag, "GMS SecurityException during anonymous auth (broker/package visibility): ${e.localizedMessage}")
                auth = null
            } catch (e: Throwable) {
                Log.w(tag, "Anonymous sign-in or GMS Auth notice: ${e.localizedMessage}")
            }
        }

        // Fallback to consistent stored user ID
        currentUserId = getOrCreateStableUserId()
        Log.d(tag, "Using stable device user ID: $currentUserId")
    }

    private fun getOrCreateStableUserId(): String {
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        var userId = prefs.getString("custom_user_id", null)
            ?: prefs.getString("firebase_user_id", null)
            ?: prefs.getString("device_user_id", null)

        if (userId.isNullOrBlank()) {
            userId = "user_" + UUID.randomUUID().toString()
            prefs.edit()
                .putString("firebase_user_id", userId)
                .putString("device_user_id", userId)
                .apply()
        }
        return userId
    }

    private fun getAllUserIds(): Set<String> {
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        val set = mutableSetOf<String>()
        if (currentUserId.isNotBlank()) set.add(currentUserId)
        prefs.getString("custom_user_id", null)?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        prefs.getString("firebase_user_id", null)?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        prefs.getString("device_user_id", null)?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        auth?.currentUser?.uid?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        return set
    }

    suspend fun setCustomUserId(newUserId: String) = withContext(Dispatchers.IO) {
        val trimmed = newUserId.trim()
        if (trimmed.isBlank()) return@withContext
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        currentUserId = trimmed
        prefs.edit()
            .putString("custom_user_id", trimmed)
            .putString("firebase_user_id", trimmed)
            .apply()
        Log.d(tag, "Set custom user ID / UUID: $trimmed")
        startFirestoreListeners()
        syncAllLocalBeersToFirestore()
    }

    suspend fun signInWithEmailAndPassword(email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        val currentAuth = auth ?: try {
            FirebaseAuth.getInstance().also { auth = it }
        } catch (e: Throwable) {
            return@withContext Result.failure(Exception("FirebaseAuth nicht verfügbar: ${e.localizedMessage}"))
        }

        try {
            val res = currentAuth.signInWithEmailAndPassword(email.trim(), pass).await()
            val uid = res.user?.uid ?: return@withContext Result.failure(Exception("Anmeldung fehlgeschlagen: Keine UID empfangen"))
            val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
            currentUserId = uid
            prefs.edit()
                .putString("firebase_user_id", uid)
                .putString("custom_user_id", uid)
                .apply()
            Log.d(tag, "Successfully signed in with email, UID: $uid")
            startFirestoreListeners()
            syncAllLocalBeersToFirestore()
            Result.success(uid)
        } catch (e: Throwable) {
            Log.e(tag, "Email sign in error: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    /**
     * Ensures that an image received from Firestore (as Base64) is decoded and stored as a local file,
     * so that Coil and local file loaders can load it fast and offline.
     */
    private fun ensureLocalCachedImage(beerId: String, currentPath: String?, imageBase64: String?): String? {
        if (!currentPath.isNullOrBlank() && File(currentPath).exists()) {
            return currentPath
        }
        if (imageBase64.isNullOrBlank()) return currentPath

        val cacheFile = File(beerImagesDir, "beer_${beerId}.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile.absolutePath
        }

        val success = ImageUtils.saveBase64ToFile(imageBase64, cacheFile)
        return if (success) cacheFile.absolutePath else currentPath
    }

    private fun startFirestoreListeners() {
        val db = firestore ?: return

        synchronized(activeListeners) {
            activeListeners.forEach { it.remove() }
            activeListeners.clear()
        }

        fun processAndMergeSnapshots() {
            if (!hasReceivedAnySnapshot && collectionDocs.isEmpty()) {
                _syncState.value = SyncState.Syncing
                return
            }
            hasReceivedAnySnapshot = true

            // Gather all documents currently alive in Firestore across all listened collections
            val allRemote = mutableListOf<SavedBeerItem>()
            for (docMap in collectionDocs.values) {
                for ((_, rawRemote) in docMap) {
                    if (rawRemote.id in deletedBeerIds) continue
                    val localPath = ensureLocalCachedImage(rawRemote.id, rawRemote.imagePath, rawRemote.imageBase64)
                    val remote = if (localPath != null && localPath != rawRemote.imagePath) {
                        rawRemote.copy(imagePath = localPath)
                    } else {
                        rawRemote
                    }
                    allRemote.add(remote)
                }
            }

            // Include local beers that are pending initial sync to Firestore
            val pendingLocals = _beersFlow.value.filter {
                it.id in pendingLocalBeerIds && it.id !in deletedBeerIds
            }

            val combined = (allRemote + pendingLocals).filterNot { it.id in deletedBeerIds }
            val (mergedList, duplicateIds) = deduplicateBeerList(combined)
            _beersFlow.value = mergedList
            persistLocalCache(mergedList)
            _syncState.value = SyncState.Success(mergedList.size)

            if (duplicateIds.isNotEmpty()) {
                deletedBeerIds.addAll(duplicateIds)
                persistDeletedBeerIds()
                coroutineScope.launch {
                    val db = firestore ?: return@launch
                    for (dupId in duplicateIds) {
                        try {
                            for (uId in getAllUserIds()) {
                                db.collection("users").document(uId).collection("beers").document(dupId).delete()
                                db.collection("users").document(uId).collection("drinks").document(dupId).delete()
                            }
                            db.collection("beers").document(dupId).delete()
                            db.collection("drinks").document(dupId).delete()
                            Log.d(tag, "Cleaned up redundant duplicate document $dupId from Firestore")
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

        val userIds = getAllUserIds()
        val newListeners = mutableListOf<ListenerRegistration>()

        for (uId in userIds) {
            // Listen to users/$uId/beers
            try {
                val userBeersRef = db.collection("users").document(uId).collection("beers")
                val key = "user_beers_$uId"
                val listener = userBeersRef.addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.d(tag, "Listen notice for users/$uId/beers: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val currentInCollection = mutableMapOf<String, SavedBeerItem>()
                        for (doc in snapshots.documents) {
                            if (doc.id in deletedBeerIds) continue
                            val data = doc.data ?: continue
                            val beer = SavedBeerItem.fromMap(doc.id, data)
                            if (beer.name.isNotBlank() && beer.id !in deletedBeerIds) {
                                currentInCollection[beer.id] = beer
                                pendingLocalBeerIds.remove(beer.id)
                            }
                        }
                        collectionDocs[key] = currentInCollection
                        processAndMergeSnapshots()
                    }
                }
                newListeners.add(listener)
            } catch (e: Throwable) {
                Log.d(tag, "Could not start listener for users/$uId/beers: ${e.localizedMessage}")
            }

            // Listen to users/$uId/drinks
            try {
                val userDrinksRef = db.collection("users").document(uId).collection("drinks")
                val key = "user_drinks_$uId"
                val listener = userDrinksRef.addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.d(tag, "Listen notice for users/$uId/drinks: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val currentInCollection = mutableMapOf<String, SavedBeerItem>()
                        for (doc in snapshots.documents) {
                            if (doc.id in deletedBeerIds) continue
                            val data = doc.data ?: continue
                            val beer = SavedBeerItem.fromMap(doc.id, data)
                            if (beer.name.isNotBlank() && beer.id !in deletedBeerIds) {
                                currentInCollection[beer.id] = beer
                                pendingLocalBeerIds.remove(beer.id)
                            }
                        }
                        collectionDocs[key] = currentInCollection
                        processAndMergeSnapshots()
                    }
                }
                newListeners.add(listener)
            } catch (e: Throwable) {
                Log.d(tag, "Could not start listener for users/$uId/drinks: ${e.localizedMessage}")
            }
        }

        // Listen to root collection "beers"
        try {
            val globalBeersRef = db.collection("beers")
            val key = "global_beers"
            val listener = globalBeersRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.d(tag, "Global 'beers' listen notice: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val currentInCollection = mutableMapOf<String, SavedBeerItem>()
                    for (doc in snapshots.documents) {
                        if (doc.id in deletedBeerIds) continue
                        val data = doc.data ?: continue
                        val beer = SavedBeerItem.fromMap(doc.id, data)
                        if (beer.name.isNotBlank() && beer.id !in deletedBeerIds) {
                            currentInCollection[beer.id] = beer
                            pendingLocalBeerIds.remove(beer.id)
                        }
                    }
                    collectionDocs[key] = currentInCollection
                    processAndMergeSnapshots()
                }
            }
            newListeners.add(listener)
        } catch (e: Throwable) {
            Log.d(tag, "Could not start global 'beers' listener: ${e.localizedMessage}")
        }

        // Listen to root collection "drinks"
        try {
            val globalDrinksRef = db.collection("drinks")
            val key = "global_drinks"
            val listener = globalDrinksRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.d(tag, "Global 'drinks' listen notice: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val currentInCollection = mutableMapOf<String, SavedBeerItem>()
                    for (doc in snapshots.documents) {
                        if (doc.id in deletedBeerIds) continue
                        val data = doc.data ?: continue
                        val beer = SavedBeerItem.fromMap(doc.id, data)
                        if (beer.name.isNotBlank() && beer.id !in deletedBeerIds) {
                            currentInCollection[beer.id] = beer
                            pendingLocalBeerIds.remove(beer.id)
                        }
                    }
                    collectionDocs[key] = currentInCollection
                    processAndMergeSnapshots()
                }
            }
            newListeners.add(listener)
        } catch (e: Throwable) {
            Log.d(tag, "Could not start global 'drinks' listener: ${e.localizedMessage}")
        }

        synchronized(activeListeners) {
            activeListeners.addAll(newListeners)
        }

        // Initial process call to ensure sync state succeeds even if empty
        processAndMergeSnapshots()
    }

    /**
     * Merges duplicate entries representing the same drink into single unique items,
     * prioritizing higher ratings, non-blank notes, and valid images.
     * Returns the deduplicated list along with any redundant duplicate IDs that should be cleaned up.
     */
    fun deduplicateBeerList(items: List<SavedBeerItem>): Pair<List<SavedBeerItem>, List<String>> {
        val uniqueList = mutableListOf<SavedBeerItem>()
        val duplicateIdsToDelete = mutableListOf<String>()

        for (item in items) {
            val existingIndex = uniqueList.indexOfFirst {
                it.id == item.id || isSameDrink(it.name, it.brandOrProducer, item.name, item.brandOrProducer)
            }

            if (existingIndex == -1) {
                uniqueList.add(item)
            } else {
                val existing = uniqueList[existingIndex]
                if (item.id != existing.id) {
                    duplicateIdsToDelete.add(item.id)
                }

                val merged = if (item.timestamp > existing.timestamp ||
                    (item.rating > existing.rating) ||
                    (item.userNotes.isNotBlank() && existing.userNotes.isBlank())
                ) {
                    item.copy(
                        id = existing.id,
                        rating = if (item.rating > 0f) item.rating else existing.rating,
                        userNotes = if (item.userNotes.isNotBlank()) item.userNotes else existing.userNotes,
                        imagePath = item.imagePath ?: existing.imagePath,
                        imageBase64 = item.imageBase64 ?: existing.imageBase64
                    )
                } else {
                    existing.copy(
                        rating = if (existing.rating > 0f) existing.rating else item.rating,
                        userNotes = if (existing.userNotes.isNotBlank()) existing.userNotes else item.userNotes,
                        imagePath = existing.imagePath ?: item.imagePath,
                        imageBase64 = existing.imageBase64 ?: item.imageBase64
                    )
                }
                uniqueList[existingIndex] = merged
            }
        }
        return Pair(uniqueList.sortedByDescending { it.timestamp }, duplicateIdsToDelete)
    }

    private fun loadLocalCache() {
        if (!localCacheFile.exists()) {
            _beersFlow.value = emptyList()
            return
        }

        try {
            val content = localCacheFile.readText()
            val list = json.decodeFromString<List<SavedBeerItem>>(content)
            val filtered = list.filterNot { it.id in deletedBeerIds }
            val (deduped, _) = deduplicateBeerList(filtered)
            _beersFlow.value = deduped
            if (deduped.size != list.size) {
                persistLocalCache(deduped)
            }
        } catch (e: Throwable) {
            Log.w(tag, "Failed to read local beer cache: ${e.localizedMessage}")
            _beersFlow.value = emptyList()
        }
    }

    private fun persistLocalCache(list: List<SavedBeerItem>) {
        try {
            val serialized = json.encodeToString(list)
            localCacheFile.writeText(serialized)
        } catch (e: Throwable) {
            Log.w(tag, "Failed to write local beer cache: ${e.localizedMessage}")
        }
    }

    suspend fun saveBeer(
        drink: DrinkDetails,
        listType: BeerListType,
        imagePath: String? = null,
        rating: Float = 0f,
        userNotes: String = "",
        imageBase64: String? = null
    ): SavedBeerItem = withContext(Dispatchers.IO) {
        ensureInitialized()
        val existingItem = findMatchingBeer(drink)

        // If this drink is already in the database:
        if (existingItem != null) {
            Log.i(tag, "Drink '${drink.name}' already exists in DB (ID: ${existingItem.id}, list: ${existingItem.listType}). Preventing duplicate save.")

            // If already in the target list and no new rating/notes provided, return existing item without creating duplicates
            if (existingItem.listType == listType && rating <= 0f && userNotes.isBlank()) {
                return@withContext existingItem
            }

            // Updating status or rating/notes of the existing item
            val beerId = existingItem.id
            val resolvedBase64 = imageBase64 ?: existingItem.imageBase64 ?: imagePath?.let { path ->
                val file = File(path)
                if (file.exists()) ImageUtils.fileToBase64(file) else null
            }
            val resolvedImagePath = ensureLocalCachedImage(beerId, imagePath ?: existingItem.imagePath, resolvedBase64)

            val updatedItem = existingItem.copy(
                listType = listType,
                imagePath = resolvedImagePath ?: existingItem.imagePath,
                imageBase64 = resolvedBase64 ?: existingItem.imageBase64,
                rating = if (rating > 0f) rating else existingItem.rating,
                userNotes = if (userNotes.isNotBlank()) userNotes else existingItem.userNotes,
                timestamp = System.currentTimeMillis()
            )

            // Ensure removed from deleted tombstones and registered as pending sync
            deletedBeerIds.remove(beerId)
            persistDeletedBeerIds()
            pendingLocalBeerIds.add(beerId)

            val updatedList = listOf(updatedItem) + _beersFlow.value.filterNot { it.id == beerId }
            _beersFlow.value = updatedList
            persistLocalCache(updatedList)
            syncBeerToFirestore(updatedItem)
            return@withContext updatedItem
        }

        // New unique drink not yet in DB
        val beerId = UUID.randomUUID().toString()

        val resolvedBase64 = imageBase64 ?: imagePath?.let { path ->
            val file = File(path)
            if (file.exists()) ImageUtils.fileToBase64(file) else null
        }

        val resolvedImagePath = ensureLocalCachedImage(beerId, imagePath, resolvedBase64)

        val beerItem = SavedBeerItem.fromDrinkDetails(
            id = beerId,
            drink = drink,
            listType = listType,
            imagePath = resolvedImagePath,
            imageUrl = null,
            imageBase64 = resolvedBase64,
            rating = rating,
            userNotes = userNotes,
            timestamp = System.currentTimeMillis()
        )

        // Ensure removed from deleted tombstones and registered as pending sync
        deletedBeerIds.remove(beerId)
        persistDeletedBeerIds()
        pendingLocalBeerIds.add(beerId)

        val updatedList = listOf(beerItem) + _beersFlow.value
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)
        syncBeerToFirestore(beerItem)

        beerItem
    }

    suspend fun updateBeerStatus(
        beerId: String,
        newType: BeerListType
    ) = withContext(Dispatchers.IO) {
        val item = _beersFlow.value.find { it.id == beerId } ?: return@withContext
        val updated = item.copy(listType = newType, timestamp = System.currentTimeMillis())

        val updatedList = listOf(updated) + _beersFlow.value.filterNot { it.id == beerId }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        syncBeerToFirestore(updated)
    }

    suspend fun updateBeerRatingAndNotes(
        beerId: String,
        rating: Float,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val item = _beersFlow.value.find { it.id == beerId } ?: return@withContext
        val updated = item.copy(
            rating = rating,
            userNotes = notes,
            timestamp = System.currentTimeMillis()
        )

        val updatedList = listOf(updated) + _beersFlow.value.filterNot { it.id == beerId }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        syncBeerToFirestore(updated)
    }

    suspend fun deleteBeer(beerId: String) = withContext(Dispatchers.IO) {
        val itemToDelete = _beersFlow.value.find { it.id == beerId }

        val allTargetIds = mutableSetOf<String>()
        allTargetIds.add(beerId)
        if (itemToDelete != null) {
            _beersFlow.value
                .filter { isSameDrink(it.name, it.brandOrProducer, itemToDelete.name, itemToDelete.brandOrProducer) }
                .forEach { allTargetIds.add(it.id) }
            for (docMap in collectionDocs.values) {
                docMap.values
                    .filter { isSameDrink(it.name, it.brandOrProducer, itemToDelete.name, itemToDelete.brandOrProducer) }
                    .forEach { allTargetIds.add(it.id) }
            }
        }

        // Register in deleted set to prevent any resurrecting snapshot
        deletedBeerIds.addAll(allTargetIds)
        persistDeletedBeerIds()

        // Remove from pending local saves
        pendingLocalBeerIds.removeAll(allTargetIds)

        // Remove from all active Firestore collection maps
        for ((key, map) in collectionDocs) {
            val filtered = map.filterKeys { it !in allTargetIds }
            collectionDocs[key] = filtered
        }

        // Update local StateFlow and persistent JSON cache immediately
        val updatedList = _beersFlow.value.filterNot { it.id in allTargetIds }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        // Delete local cached image files
        for (targetId in allTargetIds) {
            try {
                val cacheFile = File(beerImagesDir, "beer_${targetId}.jpg")
                if (cacheFile.exists()) cacheFile.delete()
            } catch (_: Throwable) {}
        }

        ensureInitialized()

        // Delete from Firestore across all user and global collections
        try {
            val db = firestore
            if (db != null) {
                val userIds = getAllUserIds()
                for (targetId in allTargetIds) {
                    for (uId in userIds) {
                        try {
                            db.collection("users").document(uId).collection("beers").document(targetId).delete()
                            db.collection("users").document(uId).collection("drinks").document(targetId).delete()
                        } catch (_: Throwable) {}
                    }
                    try {
                        db.collection("beers").document(targetId).delete()
                        db.collection("drinks").document(targetId).delete()
                    } catch (_: Throwable) {}
                }
                Log.d(tag, "Successfully deleted beer(s) $allTargetIds from Firestore")
            }
        } catch (e: Throwable) {
            Log.e(tag, "Failed to delete beer from Firestore: ${e.localizedMessage}", e)
        }
    }

    private suspend fun syncBeerToFirestore(beer: SavedBeerItem) {
        ensureInitialized()

        try {
            val db = firestore
            if (db != null) {
                // Ensure imageBase64 is generated if local imagePath exists but imageBase64 is missing
                val effectiveBeer = if (beer.imageBase64.isNullOrBlank() && !beer.imagePath.isNullOrBlank()) {
                    val file = File(beer.imagePath)
                    if (file.exists()) {
                        val b64 = ImageUtils.fileToBase64(file)
                        if (b64 != null) {
                            val updated = beer.copy(imageBase64 = b64)
                            // Update local list with base64
                            val updatedList = _beersFlow.value.map { if (it.id == beer.id) updated else it }
                            _beersFlow.value = updatedList
                            persistLocalCache(updatedList)
                            updated
                        } else beer
                    } else beer
                } else beer

                val mapData = effectiveBeer.toMap()
                val userIds = getAllUserIds()

                for (uId in userIds) {
                    try {
                        db.collection("users")
                            .document(uId)
                            .collection("beers")
                            .document(effectiveBeer.id)
                            .set(mapData, SetOptions.merge())
                    } catch (e: Throwable) {
                        Log.d(tag, "Sync to users/$uId/beers notice: ${e.localizedMessage}")
                    }
                }

                try {
                    db.collection("beers")
                        .document(effectiveBeer.id)
                        .set(mapData, SetOptions.merge())
                } catch (e: Throwable) {
                    Log.d(tag, "Sync to root beers notice: ${e.localizedMessage}")
                }

                Log.d(tag, "Successfully synced beer '${effectiveBeer.name}' (${effectiveBeer.id}) with image to Firestore")
            } else {
                Log.w(tag, "Cannot sync beer to Firestore: db is null")
            }
        } catch (e: Throwable) {
            Log.e(tag, "Failed to sync beer '${beer.name}' to Firestore: ${e.localizedMessage}", e)
        }
    }

    private suspend fun syncAllLocalBeersToFirestore() {
        val localList = _beersFlow.value.filterNot { it.id in deletedBeerIds }
        if (localList.isEmpty()) return

        val db = firestore ?: return
        if (currentUserId.isBlank()) return

        // Enrich any beers that have local image files but no Base64 yet
        val enrichedList = localList.map { beer ->
            if (beer.imageBase64.isNullOrBlank() && !beer.imagePath.isNullOrBlank()) {
                val file = File(beer.imagePath)
                if (file.exists()) {
                    val b64 = ImageUtils.fileToBase64(file)
                    if (b64 != null) beer.copy(imageBase64 = b64) else beer
                } else beer
            } else beer
        }
        if (enrichedList != localList) {
            _beersFlow.value = enrichedList
            persistLocalCache(enrichedList)
        }

        try {
            val batch = db.batch()
            val userBeersRef = db.collection("users")
                .document(currentUserId)
                .collection("beers")

            for (beer in enrichedList) {
                val docRef = userBeersRef.document(beer.id)
                batch.set(docRef, beer.toMap(), SetOptions.merge())
            }

            batch.commit().await()
            Log.d(tag, "Successfully batch-synced ${enrichedList.size} local beers with images to Firestore")
        } catch (e: Throwable) {
            Log.w(tag, "Batch sync local beers notice: ${e.localizedMessage}")
        }
    }

    fun findMatchingBeer(drink: DrinkDetails): SavedBeerItem? {
        return findMatchingBeer(drink.name, drink.brandOrProducer)
    }

    fun findMatchingBeer(name: String, brand: String? = null): SavedBeerItem? {
        val inFlow = _beersFlow.value.find { saved ->
            isSameDrink(saved.name, saved.brandOrProducer, name, brand)
        }
        if (inFlow != null) return inFlow

        for (docMap in collectionDocs.values) {
            val inDocs = docMap.values.find { saved ->
                saved.id !in deletedBeerIds && isSameDrink(saved.name, saved.brandOrProducer, name, brand)
            }
            if (inDocs != null) return inDocs
        }
        return null
    }

    fun findSavedBeerByName(name: String): SavedBeerItem? {
        return findMatchingBeer(name, null)
    }

    companion object {
        fun normalizeString(str: String?): String {
            if (str.isNullOrBlank()) return ""
            val germanReplaced = str.trim()
                .lowercase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
            val decomposed = Normalizer.normalize(germanReplaced, Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^a-z0-9]"), "")
        }

        /**
         * Returns true if two drink names are identical (case-insensitive and whitespace/punctuation/diacritics normalized).
         * A drink is considered a duplicate if the name is identical.
         */
        fun isDuplicateName(nameA: String?, nameB: String?): Boolean {
            if (nameA.isNullOrBlank() || nameB.isNullOrBlank()) return false
            val trimA = nameA.trim()
            val trimB = nameB.trim()
            if (trimA.equals(trimB, ignoreCase = true)) return true
            val normA = normalizeString(trimA)
            val normB = normalizeString(trimB)
            return normA.isNotEmpty() && normA == normB
        }

        fun isSameDrink(
            nameA: String,
            brandA: String?,
            nameB: String,
            brandB: String?
        ): Boolean {
            // Rule: A drink is considered a duplicate if the name is identical.
            // Duplicates must not be added to the database.
            if (isDuplicateName(nameA, nameB)) return true

            // Also check variations where brand is prepended to the name in one entry
            val normNameA = normalizeString(nameA)
            val normNameB = normalizeString(nameB)
            if (normNameA.isEmpty() || normNameB.isEmpty()) return false

            val normBrandA = normalizeString(brandA)
            val normBrandB = normalizeString(brandB)

            val cleanNameA = if (normBrandA.isNotEmpty() && normNameA.startsWith(normBrandA)) {
                normNameA.removePrefix(normBrandA)
            } else normNameA
            val cleanNameB = if (normBrandB.isNotEmpty() && normNameB.startsWith(normBrandB)) {
                normNameB.removePrefix(normBrandB)
            } else normNameB

            if (cleanNameA.isNotEmpty() && cleanNameA == cleanNameB) return true
            if (cleanNameA.isNotEmpty() && cleanNameA == normNameB) return true
            if (cleanNameB.isNotEmpty() && cleanNameB == normNameA) return true

            return false
        }
    }
}