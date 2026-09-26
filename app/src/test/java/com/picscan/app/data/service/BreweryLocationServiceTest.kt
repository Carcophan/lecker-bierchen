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
    fun testFindPredefinedLocation_cities() {
        val munich = BreweryLocationService.findPredefinedLocation("München, Bayern, Deutschland")
        assertNotNull(munich)
        assertEquals(48.1371, munich!!.latitude, 0.01)
        assertEquals(11.5761, munich.longitude, 0.01)

        val dublin = BreweryLocationService.findPredefinedLocation("St. James's Gate", "Guinness Dublin")
        assertNotNull(dublin)
        assertEquals(53.3498, dublin!!.latitude, 0.01)
        assertEquals(-6.2603, dublin.longitude, 0.01)

        val pilsen = BreweryLocationService.findPredefinedLocation("Plzeň, Tschechien")
        assertNotNull(pilsen)
        assertEquals(49.7474, pilsen!!.latitude, 0.01)

        val unknown = BreweryLocationService.findPredefinedLocation("FantasieOrt12345")
        assertNull(unknown)
    }
}
