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
    fun testIsSameDrink_identicalNameIsDuplicateRegardlessOfBrand() {
        // According to requirement: A drink is considered a duplicate if the name is identical
        assertTrue(
            FirebaseBeerRepository.isSameDrink(
                nameA = "Helles",
                brandA = "Augustiner",
                nameB = "Helles",
                brandB = "Tegernseer"
            )
        )
    }

    @Test
    fun testIsDuplicateName_exactAndNormalized() {
        assertTrue(FirebaseBeerRepository.isDuplicateName("Augustiner Helles", "Augustiner Helles"))
        assertTrue(FirebaseBeerRepository.isDuplicateName("  augustiner helles  ", "AUGUSTINER HELLES"))
        assertTrue(FirebaseBeerRepository.isDuplicateName("Paulaner Hefe-Weißbier", "Paulaner Hefe-Weissbier"))
        assertTrue(FirebaseBeerRepository.isDuplicateName("Corona Extra", "corona extra"))
        assertFalse(FirebaseBeerRepository.isDuplicateName("Augustiner Helles", "Augustiner Edelstoff"))
        assertFalse(FirebaseBeerRepository.isDuplicateName("Beck's", "Beck's Gold"))
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
