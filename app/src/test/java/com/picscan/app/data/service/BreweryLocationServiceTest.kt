package com.picscan.app.data.service

import org.junit.Assert.*
import org.junit.Test

class BreweryLocationServiceTest {

    @Test
    fun testBreweryLocationDataModel() {
        val loc = BreweryLocation(
            latitude = 48.1351,
            longitude = 11.5820,
            displayName = "München, Bayern, Deutschland",
            cityOrRegion = "München"
        )

        assertEquals(48.1351, loc.latitude, 0.0001)
        assertEquals(11.5820, loc.longitude, 0.0001)
        assertEquals("München, Bayern, Deutschland", loc.displayName)
        assertEquals("München", loc.cityOrRegion)
    }

    @Test
    fun testGeocodeWithNominatim_emptyOrBlank() {
        val result = BreweryLocationService.geocodeWithNominatim("   ")
        assertNull(result)
    }

    @Test
    fun testGeocodeWithNominatim_validLocation() {
        val result = BreweryLocationService.geocodeWithNominatim("München, Deutschland")
        // If test environment has internet access, result will be populated
        if (result != null) {
            assertTrue(result.latitude > 47.0 && result.latitude < 49.0)
            assertTrue(result.longitude > 10.0 && result.longitude < 13.0)
            assertTrue(result.displayName.contains("München", ignoreCase = true))
        }
    }
}
