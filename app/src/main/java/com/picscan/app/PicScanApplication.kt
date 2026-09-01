package com.picscan.app

import android.app.Application
import com.picscan.app.data.repository.ApiKeyPreferenceRepository
import com.picscan.app.data.repository.GeminiDrinkScannerRepository
import com.picscan.app.data.repository.HistoryRepository

class PicScanApplication : Application() {

    lateinit var apiKeyRepository: ApiKeyPreferenceRepository
        private set

    lateinit var geminiRepository: GeminiDrinkScannerRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        apiKeyRepository = ApiKeyPreferenceRepository(this)
        geminiRepository = GeminiDrinkScannerRepository()
        historyRepository = HistoryRepository(this)
    }
}
