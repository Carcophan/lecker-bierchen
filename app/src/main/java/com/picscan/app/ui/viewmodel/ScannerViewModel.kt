package com.picscan.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.picscan.app.data.model.DrinkDetails
import com.picscan.app.data.model.ScanHistoryItem
import com.picscan.app.data.repository.ApiKeyPreferenceRepository
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
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK
)

class ScannerViewModel(
    private val apiKeyRepo: ApiKeyPreferenceRepository,
    private val scannerRepo: GeminiDrinkScannerRepository,
    private val historyRepo: HistoryRepository
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

    fun analyzeImage(bitmap: Bitmap, onComplete: () -> Unit = {}) {
        val currentApiKey = apiKey.value
        val currentModel = selectedModel.value

        if (currentApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter your Gemini API Key in Settings to scan drinks."
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
                    errorMessage = error.localizedMessage ?: "Failed to analyze drink with Gemini AI."
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
            historyRepo: HistoryRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScannerViewModel(apiKeyRepo, scannerRepo, historyRepo) as T
            }
        }
    }
}
