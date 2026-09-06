package com.picscan.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyPreferenceRepositoryTest {

    @Test
    fun testDefaultModelIsGemini38Flash() {
        assertEquals("gemini-3.8-flash", ApiKeyPreferenceRepository.DEFAULT_MODEL)
    }

    @Test
    fun testAvailableModelsContainsGemini38FlashAndNotGemini36() {
        val modelKeys = ApiKeyPreferenceRepository.AVAILABLE_MODELS.map { it.first }
        assertTrue(modelKeys.contains("gemini-3.8-flash"))
        assertFalse(modelKeys.contains("gemini-3.6-flash"))
    }
}
