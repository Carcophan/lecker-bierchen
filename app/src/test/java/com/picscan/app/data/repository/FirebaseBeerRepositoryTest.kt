package com.picscan.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseBeerRepositoryTest {

    @Test
    fun testIsSameDrink_exactMatch() {
        assertTrue(
            FirebaseBeerRepository.isSameDrink(
                nameA = "Augustiner Helles",
                brandA = "Augustiner",
                nameB = "Augustiner Helles",
                brandB = "Augustiner"
            )
        )
    }

    @Test
    fun testIsSameDrink_caseAndWhitespaceInsensitive() {
        assertTrue(
            FirebaseBeerRepository.isSameDrink(
                nameA = "  augustiner helles  ",
                brandA = "augustiner",
                nameB = "AUGUSTINER HELLES",
                brandB = "Augustiner"
            )
        )
    }

    @Test
    fun testIsSameDrink_withUmlautsAndSpecialChars() {
        assertTrue(
            FirebaseBeerRepository.isSameDrink(
                nameA = "Paulaner Hefe-Weißbier",
                brandA = "Paulaner",
                nameB = "Paulaner Hefe-Weissbier",
                brandB = "Paulaner"
            )
        )
    }

    @Test
    fun testIsSameDrink_brandInNameVariation() {
        assertTrue(
            FirebaseBeerRepository.isSameDrink(
                nameA = "Augustiner Edelstoff",
                brandA = "Augustiner",
                nameB = "Edelstoff",
                brandB = "Augustiner"
            )
        )
    }

    @Test
    fun testIsSameDrink_differentBrandsAreNotEqual() {
        assertFalse(
            FirebaseBeerRepository.isSameDrink(
                nameA = "Helles",
                brandA = "Augustiner",
                nameB = "Helles",
                brandB = "Tegernseer"
            )
        )
    }

    @Test
    fun testIsSameDrink_differentBeersSameBrandAreNotEqual() {
        assertFalse(
            FirebaseBeerRepository.isSameDrink(
                nameA = "Augustiner Helles",
                brandA = "Augustiner",
                nameB = "Augustiner Edelstoff",
                brandB = "Augustiner"
            )
        )
    }
}
