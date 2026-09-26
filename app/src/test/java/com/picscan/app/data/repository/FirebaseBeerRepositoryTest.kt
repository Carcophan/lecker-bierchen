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

    @Test
    fun testSavedBeerItem_preservesExactOrigin() {
        val drink = com.picscan.app.data.model.DrinkDetails(
            name = "Augustiner Lagerbier Hell",
            brandOrProducer = "Augustiner-Bräu",
            origin = "München, Bayern, Deutschland"
        )
        val saved = com.picscan.app.data.model.SavedBeerItem.fromDrinkDetails(
            id = "test-123",
            drink = drink,
            listType = com.picscan.app.data.model.BeerListType.KNOWN
        )
        org.junit.Assert.assertEquals("München, Bayern, Deutschland", saved.origin)

        val map = saved.toMap()
        org.junit.Assert.assertEquals("München, Bayern, Deutschland", map["origin"])

        val restored = com.picscan.app.data.model.SavedBeerItem.fromMap("test-123", map)
        org.junit.Assert.assertEquals("München, Bayern, Deutschland", restored.origin)
        org.junit.Assert.assertEquals("München, Bayern, Deutschland", restored.toDrinkDetails().origin)
    }

    @Test
    fun testSavedBeerItem_preservesCoordinates() {
        val drink = com.picscan.app.data.model.DrinkDetails(
            name = "Erdinger Weißbier",
            brandOrProducer = "Erdinger Weißbräu",
            origin = "Erding, Bayern, Deutschland"
        )
        val saved = com.picscan.app.data.model.SavedBeerItem.fromDrinkDetails(
            id = "test-coords",
            drink = drink,
            listType = com.picscan.app.data.model.BeerListType.KNOWN,
            latitude = 48.3060,
            longitude = 11.9069
        )
        org.junit.Assert.assertEquals(48.3060, saved.latitude!!, 0.0001)
        org.junit.Assert.assertEquals(11.9069, saved.longitude!!, 0.0001)

        val map = saved.toMap()
        org.junit.Assert.assertEquals(48.3060, map["latitude"])
        org.junit.Assert.assertEquals(11.9069, map["longitude"])

        val restored = com.picscan.app.data.model.SavedBeerItem.fromMap("test-coords", map)
        org.junit.Assert.assertEquals(48.3060, restored.latitude!!, 0.0001)
        org.junit.Assert.assertEquals(11.9069, restored.longitude!!, 0.0001)
    }
}
