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

        val remoteMap = ConcurrentHashMap<String, SavedBeerItem>()

        fun processAndMergeSnapshots() {
            val currentMap = _beersFlow.value.associateBy { it.id }.toMutableMap()

            for ((id, rawRemote) in remoteMap) {
                // Ensure image from Firestore Base64 is cached locally for fast display
                val localPath = ensureLocalCachedImage(rawRemote.id, rawRemote.imagePath, rawRemote.imageBase64)
                val remote = if (localPath != null && localPath != rawRemote.imagePath) {
                    rawRemote.copy(imagePath = localPath)
                } else {
                    rawRemote
                }

                // Deduplicate by ID or by Name + Brand
                val existingByKey = currentMap.values.find {
                    it.id == id || (it.name.equals(remote.name, ignoreCase = true) &&
                            (it.brandOrProducer.isNullOrBlank() || remote.brandOrProducer.isNullOrBlank() ||
                                    it.brandOrProducer.equals(remote.brandOrProducer, ignoreCase = true)))
                }

                if (existingByKey == null) {
                    currentMap[remote.id] = remote
                } else {
                    // Pick the one with higher timestamp or rating/notes, preserving local/remote image data
                    val chosen = if (remote.timestamp > existingByKey.timestamp ||
                        (remote.rating > existingByKey.rating) ||
                        (remote.userNotes.isNotBlank() && existingByKey.userNotes.isBlank())
                    ) {
                        remote.copy(
                            id = existingByKey.id,
                            rating = if (remote.rating > 0f) remote.rating else existingByKey.rating,
                            userNotes = if (remote.userNotes.isNotBlank()) remote.userNotes else existingByKey.userNotes,
                            imagePath = remote.imagePath ?: existingByKey.imagePath,
                            imageBase64 = remote.imageBase64 ?: existingByKey.imageBase64
                        )
                    } else {
                        existingByKey.copy(
                            rating = if (existingByKey.rating > 0f) existingByKey.rating else remote.rating,
                            userNotes = if (existingByKey.userNotes.isNotBlank()) existingByKey.userNotes else remote.userNotes,
                            imagePath = existingByKey.imagePath ?: remote.imagePath,
                            imageBase64 = existingByKey.imageBase64 ?: remote.imageBase64
                        )
                    }
                    currentMap[chosen.id] = chosen
                }
            }

            val mergedList = currentMap.values.sortedByDescending { it.timestamp }
            _beersFlow.value = mergedList
            persistLocalCache(mergedList)
            _syncState.value = SyncState.Success(mergedList.size)
        }

        val userIds = getAllUserIds()
        val newListeners = mutableListOf<ListenerRegistration>()

        for (uId in userIds) {
            // Listen to users/$uId/beers
            try {
                val userBeersRef = db.collection("users").document(uId).collection("beers")
                val listener = userBeersRef.addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.d(tag, "Listen notice for users/$uId/beers: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val beer = SavedBeerItem.fromMap(doc.id, data)
                            if (beer.name.isNotBlank()) {
                                remoteMap[beer.id] = beer
                            }
                        }
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
                val listener = userDrinksRef.addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.d(tag, "Listen notice for users/$uId/drinks: ${error.localizedMessage}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        for (doc in snapshots.documents) {
                            val data = doc.data ?: continue
                            val beer = SavedBeerItem.fromMap(doc.id, data)
                            if (beer.name.isNotBlank()) {
                                remoteMap[beer.id] = beer
                            }
                        }
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
            val listener = globalBeersRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.d(tag, "Global 'beers' listen notice: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val data = doc.data ?: continue
                        val beer = SavedBeerItem.fromMap(doc.id, data)
                        if (beer.name.isNotBlank()) {
                            remoteMap[beer.id] = beer
                        }
                    }
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
            val listener = globalDrinksRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.d(tag, "Global 'drinks' listen notice: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val data = doc.data ?: continue
                        val beer = SavedBeerItem.fromMap(doc.id, data)
                        if (beer.name.isNotBlank()) {
                            remoteMap[beer.id] = beer
                        }
                    }
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

    private fun loadLocalCache() {
        if (!localCacheFile.exists()) {
            _beersFlow.value = emptyList()
            return
        }

        try {
            val content = localCacheFile.readText()
            val list = json.decodeFromString<List<SavedBeerItem>>(content)
            _beersFlow.value = list.sortedByDescending { it.timestamp }
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
        val existingItem = _beersFlow.value.find {
            it.name.equals(drink.name, ignoreCase = true) ||
            (!it.brandOrProducer.isNullOrBlank() && it.brandOrProducer.equals(drink.brandOrProducer, ignoreCase = true) && it.name.equals(drink.name, ignoreCase = true))
        }

        val beerId = existingItem?.id ?: UUID.randomUUID().toString()

        // Resolve Base64 image: Use explicitly provided, or convert local file to Base64, or reuse existing
        val resolvedBase64 = imageBase64 ?: existingItem?.imageBase64 ?: imagePath?.let { path ->
            val file = File(path)
            if (file.exists()) ImageUtils.fileToBase64(file) else null
        }

        // Ensure local file exists on this device so Coil can display it immediately
        val resolvedImagePath = ensureLocalCachedImage(beerId, imagePath ?: existingItem?.imagePath, resolvedBase64)

        val beerItem = SavedBeerItem.fromDrinkDetails(
            id = beerId,
            drink = drink,
            listType = listType,
            imagePath = resolvedImagePath,
            imageUrl = existingItem?.imageUrl,
            imageBase64 = resolvedBase64,
            rating = if (rating > 0f) rating else (existingItem?.rating ?: 0f),
            userNotes = if (userNotes.isNotBlank()) userNotes else (existingItem?.userNotes ?: ""),
            timestamp = System.currentTimeMillis()
        )

        // Update local state immediately for instant UI feedback
        val updatedList = listOf(beerItem) + _beersFlow.value.filterNot { it.id == beerId }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        // Sync with Firestore (including Base64 image)
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
        val updatedList = _beersFlow.value.filterNot { it.id == beerId }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        // Delete local cached image if present
        try {
            val cacheFile = File(beerImagesDir, "beer_${beerId}.jpg")
            if (cacheFile.exists()) cacheFile.delete()
        } catch (_: Throwable) {}

        ensureInitialized()

        try {
            val db = firestore
            if (db != null) {
                for (uId in getAllUserIds()) {
                    try {
                        db.collection("users").document(uId).collection("beers").document(beerId).delete()
                        db.collection("users").document(uId).collection("drinks").document(beerId).delete()
                    } catch (_: Throwable) {}
                }
                try {
                    db.collection("beers").document(beerId).delete()
                    db.collection("drinks").document(beerId).delete()
                } catch (_: Throwable) {}
                Log.d(tag, "Successfully deleted beer $beerId from Firestore")
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
        val localList = _beersFlow.value
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
            for (beer in enrichedList) {
                syncBeerToFirestore(beer)
            }
        }
    }

    fun findSavedBeerByName(name: String): SavedBeerItem? {
        return _beersFlow.value.find { it.name.equals(name, ignoreCase = true) }
    }
}