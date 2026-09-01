package com.picscan.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.picscan.app.data.model.DrinkDetails
import com.picscan.app.data.model.ScanHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class HistoryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val historyFile = File(context.filesDir, "scan_history.json")
    private val imagesDir = File(context.filesDir, "drink_images").apply { mkdirs() }

    private val _historyFlow = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val historyFlow: Flow<List<ScanHistoryItem>> = _historyFlow.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        if (!historyFile.exists()) {
            _historyFlow.value = emptyList()
            return
        }

        try {
            val content = historyFile.readText()
            val list = json.decodeFromString<List<ScanHistoryItem>>(content)
            _historyFlow.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            _historyFlow.value = emptyList()
        }
    }

    suspend fun saveScan(drink: DrinkDetails, bitmap: Bitmap?): ScanHistoryItem = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        var imagePath: String? = null

        if (bitmap != null) {
            val imageFile = File(imagesDir, "drink_$id.jpg")
            try {
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                imagePath = imageFile.absolutePath
            } catch (_: Exception) {}
        }

        val newItem = ScanHistoryItem(
            id = id,
            drink = drink,
            timestamp = System.currentTimeMillis(),
            imagePath = imagePath
        )

        val updatedList = listOf(newItem) + _historyFlow.value
        _historyFlow.value = updatedList
        persistHistory(updatedList)

        newItem
    }

    suspend fun deleteScan(id: String) = withContext(Dispatchers.IO) {
        val itemToDelete = _historyFlow.value.find { it.id == id }
        if (itemToDelete?.imagePath != null) {
            try {
                File(itemToDelete.imagePath).delete()
            } catch (_: Exception) {}
        }

        val updatedList = _historyFlow.value.filterNot { it.id == id }
        _historyFlow.value = updatedList
        persistHistory(updatedList)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.forEach { it.delete() }
        _historyFlow.value = emptyList()
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }

    private fun persistHistory(list: List<ScanHistoryItem>) {
        try {
            val serialized = json.encodeToString(list)
            historyFile.writeText(serialized)
        } catch (_: Exception) {}
    }
}
