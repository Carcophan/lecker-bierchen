package com.picscan.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.picscan.app.data.model.BeerListType
import com.picscan.app.data.model.DrinkDetails
import com.picscan.app.data.model.SavedBeerItem
import com.picscan.app.data.model.ScanHistoryItem
import com.picscan.app.data.repository.ApiKeyPreferenceRepository
import com.picscan.app.data.repository.FirebaseBeerRepository
import com.picscan.app.data.repository.GeminiDrinkScannerRepository
import com.picscan.app.data.repository.HistoryRepository
import com.picscan.app.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isAnalyzing: Boolean = false,
    val currentDrink: DrinkDetails? = null,
    val currentImageBitmap: Bitmap? = null,
    val currentImagePath: String? = null,
    val errorMessage: String? = null,
    val isFlashOn: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val saveSuccessMessage: String? = null
)

class ScannerViewModel(
    private val apiKeyRepo: ApiKeyPreferenceRepository,
    private val scannerRepo: GeminiDrinkScannerRepository,
    private val historyRepo: HistoryRepository,
    private val beerRepo: FirebaseBeerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val apiKey: StateFlow<String> = apiKeyRepo.apiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val selectedModel: StateFlow<String> = apiKeyRepo.selectedModelFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ApiKeyPreferenceRepository.DEFAULT_MODEL
    )

    val scanHistory: StateFlow<List<ScanHistoryItem>> = historyRepo.historyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSavedBeers: StateFlow<List<SavedBeerItem>> = beerRepo.allBeersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val knownBeers: StateFlow<List<SavedBeerItem>> = beerRepo.knownBeersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val wishlistBeers: StateFlow<List<SavedBeerItem>> = beerRepo.wishlistBeersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleFlash() {
        _uiState.value = _uiState.value.copy(isFlashOn = !_uiState.value.isFlashOn)
    }

    fun toggleCameraFacing() {
        val newFacing = if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _uiState.value = _uiState.value.copy(lensFacing = newFacing)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSaveSuccessMessage() {
        _uiState.value = _uiState.value.copy(saveSuccessMessage = null)
    }

    fun analyzeImage(bitmap: Bitmap, onComplete: () -> Unit = {}) {
        val currentApiKey = apiKey.value
        val currentModel = selectedModel.value

        if (currentApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Bitte gib deinen Gemini-API-Schlüssel in den Einstellungen ein, um Getränke zu scannen."
            )
            return
        }

        viewModelScope.launch {
            // Scale bitmap down to max 1024px to optimize upload data, token usage, and local storage
            val scaledBitmap = ImageUtils.scaleBitmapDown(bitmap)

            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                errorMessage = null,
                currentImageBitmap = scaledBitmap
            )

            val result = scannerRepo.analyzeDrinkImage(
                bitmap = scaledBitmap,
                apiKey = currentApiKey,
                modelName = currentModel
            )

            result.onSuccess { drinkDetails ->
                val savedItem = historyRepo.saveScan(drinkDetails, scaledBitmap)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    currentDrink = drinkDetails,
                    currentImagePath = savedItem.imagePath
                )
                onComplete()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = error.localizedMessage ?: "Getränkeanalyse mit Gemini AI fehlgeschlagen."
                )
            }
        }
    }

    fun selectHistoryItem(item: ScanHistoryItem) {
        _uiState.value = _uiState.value.copy(
            currentDrink = item.drink,
            currentImagePath = item.imagePath,
            currentImageBitmap = null
        )
    }

    fun selectSavedBeer(beer: SavedBeerItem) {
        _uiState.value = _uiState.value.copy(
            currentDrink = beer.toDrinkDetails(),
            currentImagePath = beer.imagePath,
            currentImageBitmap = null
        )
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            historyRepo.deleteScan(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearAllHistory()
        }
    }

    fun saveBeerToList(
        drink: DrinkDetails,
        listType: BeerListType,
        rating: Float = 0f,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val imgPath = _uiState.value.currentImagePath
            beerRepo.saveBeer(
                drink = drink,
                listType = listType,
                imagePath = imgPath,
                rating = rating,
                userNotes = notes
            )
            val msg = when (listType) {
                BeerListType.KNOWN -> "🍺 In 'Kenne ich' (Firebase) gespeichert!"
                BeerListType.WISHLIST -> "📌 In 'Will ich' (Firebase) gespeichert!"
            }
            _uiState.value = _uiState.value.copy(saveSuccessMessage = msg)
        }
    }

    fun updateBeerStatus(beerId: String, newType: BeerListType) {
        viewModelScope.launch {
            beerRepo.updateBeerStatus(beerId, newType)
            val msg = when (newType) {
                BeerListType.KNOWN -> "Zu 'Kenne ich' verschoben! 🍺"
                BeerListType.WISHLIST -> "Auf die Wunschliste 'Will ich' verschoben! 📌"
            }
            _uiState.value = _uiState.value.copy(saveSuccessMessage = msg)
        }
    }

    fun updateBeerRatingAndNotes(beerId: String, rating: Float, notes: String) {
        viewModelScope.launch {
            beerRepo.updateBeerRatingAndNotes(beerId, rating, notes)
            _uiState.value = _uiState.value.copy(saveSuccessMessage = "Bewertung & Notiz aktualisiert! ✨")
        }
    }

    fun deleteBeerFromList(beerId: String) {
        viewModelScope.launch {
            beerRepo.deleteBeer(beerId)
        }
    }

    fun findSavedBeerByName(name: String): SavedBeerItem? {
        return beerRepo.findSavedBeerByName(name)
    }

    fun saveApiKey(newKey: String) {
        viewModelScope.launch {
            apiKeyRepo.saveApiKey(newKey)
        }
    }

    fun selectModel(modelName: String) {
        viewModelScope.launch {
            apiKeyRepo.saveSelectedModel(modelName)
        }
    }

    companion object {
        fun provideFactory(
            apiKeyRepo: ApiKeyPreferenceRepository,
            scannerRepo: GeminiDrinkScannerRepository,
            historyRepo: HistoryRepository,
            beerRepo: FirebaseBeerRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScannerViewModel(apiKeyRepo, scannerRepo, historyRepo, beerRepo) as T
            }
        }
    }
}
