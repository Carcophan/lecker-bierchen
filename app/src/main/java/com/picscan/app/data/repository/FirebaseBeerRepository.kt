package com.picscan.app.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

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

    private val _beersFlow = MutableStateFlow<List<SavedBeerItem>>(emptyList())
    val allBeersFlow: Flow<List<SavedBeerItem>> = _beersFlow.asStateFlow()

    val knownBeersFlow: Flow<List<SavedBeerItem>> = _beersFlow.map { list ->
        list.filter { it.listType == BeerListType.KNOWN }.sortedByDescending { it.timestamp }
    }

    val wishlistBeersFlow: Flow<List<SavedBeerItem>> = _beersFlow.map { list ->
        list.filter { it.listType == BeerListType.WISHLIST }.sortedByDescending { it.timestamp }
    }

    private var firestoreListener: ListenerRegistration? = null
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var currentUserId: String = ""

    private val initJob: Job

    init {
        loadLocalCache()
        initJob = coroutineScope.launch {
            initFirebase()
        }
    }

    private suspend fun ensureInitialized() {
        if (!initJob.isCompleted) {
            initJob.join()
        }
    }

    private suspend fun initFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }

            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance().apply {
                // Ensure persistent local cache is enabled for robust offline support
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    firestoreSettings = settings
                } catch (e: Exception) {
                    // Settings can only be set before any other Firestore operations
                    Log.d(tag, "Firestore settings notice: ${e.localizedMessage}")
                }
            }

            ensureUserAuthenticated()
            startFirestoreListener()

            // Push any locally cached beers to Firestore to ensure complete synchronization
            syncAllLocalBeersToFirestore()
        } catch (e: Exception) {
            Log.e(tag, "Firebase initialization error: ${e.localizedMessage}", e)
            if (currentUserId.isBlank()) {
                currentUserId = getOrCreateStableUserId()
            }
        }
    }

    private suspend fun ensureUserAuthenticated() {
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        val currentAuth = auth

        if (currentAuth != null) {
            val existingUser = currentAuth.currentUser
            if (existingUser != null) {
                currentUserId = existingUser.uid
                prefs.edit().putString("firebase_user_id", currentUserId).apply()
                Log.d(tag, "Authenticated with existing user UID: $currentUserId")
                return
            }

            try {
                val result = currentAuth.signInAnonymously().await()
                val uid = result.user?.uid
                if (!uid.isNullOrBlank()) {
                    currentUserId = uid
                    prefs.edit().putString("firebase_user_id", currentUserId).apply()
                    Log.d(tag, "Signed in anonymously with UID: $currentUserId")
                    return
                }
            } catch (e: Exception) {
                Log.w(tag, "Anonymous sign-in unavailable or failed: ${e.localizedMessage}")
            }
        }

        // Fallback to consistent stored user ID
        currentUserId = getOrCreateStableUserId()
        Log.d(tag, "Using stable device user ID: $currentUserId")
    }

    private fun getOrCreateStableUserId(): String {
        val prefs = context.getSharedPreferences("picscan_user_prefs", Context.MODE_PRIVATE)
        var userId = prefs.getString("firebase_user_id", null)
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

    private fun startFirestoreListener() {
        val db = firestore ?: return
        if (currentUserId.isBlank()) return

        firestoreListener?.remove()

        try {
            val userBeersRef = db.collection("users")
                .document(currentUserId)
                .collection("beers")

            firestoreListener = userBeersRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(tag, "Firestore listen error: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val remoteBeers = snapshots.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        SavedBeerItem.fromMap(doc.id, data)
                    }

                    // Smart Merge: combine remote items with any local items
                    val currentMap = _beersFlow.value.associateBy { it.id }.toMutableMap()
                    for (remote in remoteBeers) {
                        currentMap[remote.id] = remote
                    }

                    val mergedList = currentMap.values.sortedByDescending { it.timestamp }
                    _beersFlow.value = mergedList
                    persistLocalCache(mergedList)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start Firestore listener: ${e.localizedMessage}", e)
        }
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
        } catch (e: Exception) {
            Log.w(tag, "Failed to read local beer cache: ${e.localizedMessage}")
            _beersFlow.value = emptyList()
        }
    }

    private fun persistLocalCache(list: List<SavedBeerItem>) {
        try {
            val serialized = json.encodeToString(list)
            localCacheFile.writeText(serialized)
        } catch (e: Exception) {
            Log.w(tag, "Failed to write local beer cache: ${e.localizedMessage}")
        }
    }

    suspend fun saveBeer(
        drink: DrinkDetails,
        listType: BeerListType,
        imagePath: String? = null,
        rating: Float = 0f,
        userNotes: String = ""
    ): SavedBeerItem = withContext(Dispatchers.IO) {
        val existingItem = _beersFlow.value.find {
            it.name.equals(drink.name, ignoreCase = true) ||
            (!it.brandOrProducer.isNullOrBlank() && it.brandOrProducer.equals(drink.brandOrProducer, ignoreCase = true) && it.name.equals(drink.name, ignoreCase = true))
        }

        val beerId = existingItem?.id ?: UUID.randomUUID().toString()
        val beerItem = SavedBeerItem.fromDrinkDetails(
            id = beerId,
            drink = drink,
            listType = listType,
            imagePath = imagePath ?: existingItem?.imagePath,
            rating = if (rating > 0f) rating else (existingItem?.rating ?: 0f),
            userNotes = if (userNotes.isNotBlank()) userNotes else (existingItem?.userNotes ?: ""),
            timestamp = System.currentTimeMillis()
        )

        // Update local state immediately for instant UI feedback
        val updatedList = listOf(beerItem) + _beersFlow.value.filterNot { it.id == beerId }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        // Sync with Firestore (awaits initialization first if still initializing)
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
        val updatedList = _beersFlow.value.filterNot { it.id == beerId }
        _beersFlow.value = updatedList
        persistLocalCache(updatedList)

        ensureInitialized()

        try {
            val db = firestore
            if (db != null && currentUserId.isNotBlank()) {
                db.collection("users")
                    .document(currentUserId)
                    .collection("beers")
                    .document(beerId)
                    .delete()
                    .await()
                Log.d(tag, "Successfully deleted beer $beerId from Firestore")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete beer from Firestore: ${e.localizedMessage}", e)
        }
    }

    private suspend fun syncBeerToFirestore(beer: SavedBeerItem) {
        ensureInitialized()

        try {
            val db = firestore
            if (db != null && currentUserId.isNotBlank()) {
                db.collection("users")
                    .document(currentUserId)
                    .collection("beers")
                    .document(beer.id)
                    .set(beer.toMap(), SetOptions.merge())
                    .await()
                Log.d(tag, "Successfully synced beer '${beer.name}' (${beer.id}) to Firestore under user $currentUserId")
            } else {
                Log.w(tag, "Cannot sync beer to Firestore: db=$db, currentUserId='$currentUserId'")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync beer '${beer.name}' to Firestore: ${e.localizedMessage}", e)
        }
    }

    private suspend fun syncAllLocalBeersToFirestore() {
        val localList = _beersFlow.value
        if (localList.isEmpty()) return

        val db = firestore ?: return
        if (currentUserId.isBlank()) return

        try {
            val batch = db.batch()
            val userBeersRef = db.collection("users")
                .document(currentUserId)
                .collection("beers")

            for (beer in localList) {
                val docRef = userBeersRef.document(beer.id)
                batch.set(docRef, beer.toMap(), SetOptions.merge())
            }

            batch.commit().await()
            Log.d(tag, "Successfully batch-synced ${localList.size} local beers to Firestore")
        } catch (e: Exception) {
            Log.w(tag, "Batch sync local beers notice: ${e.localizedMessage}")
            // Fallback: sync individually
            for (beer in localList) {
                try {
                    db.collection("users")
                        .document(currentUserId)
                        .collection("beers")
                        .document(beer.id)
                        .set(beer.toMap(), SetOptions.merge())
                        .await()
                } catch (indivEx: Exception) {
                    Log.w(tag, "Individual sync for '${beer.name}' notice: ${indivEx.localizedMessage}")
                }
            }
        }
    }

    fun findSavedBeerByName(name: String): SavedBeerItem? {
        return _beersFlow.value.find { it.name.equals(name, ignoreCase = true) }
    }
}
