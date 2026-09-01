package com.picscan.app.data.repository

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.picscan.app.data.model.BeerVerdict
import com.picscan.app.data.model.DrinkDetails
import com.picscan.app.data.model.FlavorProfile
import com.picscan.app.data.model.NutritionalInfo
import com.picscan.app.data.model.ServingRecommendation
import com.picscan.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class GeminiDrinkScannerRepository {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun analyzeDrinkImage(
        bitmap: Bitmap,
        apiKey: String,
        modelName: String
    ): Result<DrinkDetails> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is missing. Please set your API key in Settings.")
            )
        }

        try {
            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )

            val scaledBitmap = ImageUtils.scaleBitmapDown(bitmap)

            val promptText = """
                You are a world-class sommelier, barista, mixologist, and beer connoisseur.
                Analyze the drink, bottle, can, cup, or beverage label in this image.
                
                BEER RATING INSTRUCTION (5-Tier Ranking Feature):
                If the beverage in the image is a BEER (or cider/malt beverage), evaluate and classify it into exactly one of these 5 tiers:
                1. "HOPFENBOMBE": Tier 1 (Supreme / Masterpiece). Intense hop-forward craft beers, Double/Triple IPAs, Imperial Stouts, high-end microbrew creations, Trappist/monastery masterpieces, specialty dry-hopped brews (e.g. BrewDog Punk IPA, Tree House, Pliny, Westvleteren, Sierra Nevada Torpedo, specialty craft breweries).
                2. "LECKER_BIERCHEN": Tier 2 (Top Quality / Mass Favorite). High quality, delicious traditional, Bavarian, Belgian, Czech, or German quality beers (e.g. Augustiner, Paulaner, Tegernseer, Weihenstephaner, Erdinger, Rothaus Tannenzäpfle, Chiemseer, Bayreuther, Guinness, Stella Artois, Pilsner Urquell, Duvel, Leffe, Ayinger).
                3. "WEGBIER": Tier 3 (Solid Kiosk / Späti Companion). Classic, solid, crisp everyday lager/pilsner, ubiquitous kiosk/späti favorites, festival session beers (e.g. Astra, Sternburg, Krombacher, Bitburger, Beck's, Jever, Warsteiner, Veltins, Heineken, Flensburger, Berliner Kindl, Stauder).
                4. "PENNERGLUECK": Tier 4 (Discount Dosenbier / Budget Cult). Cheap discount supermarket can beers, budget-friendly penny-pinchers (e.g. Oettinger, 5,0 Original, Hansa Pils, Paderborner, Karlskrone, Turmbräu, Schultenbräu, Adelskronen, Meisterbräu).
                5. "PISSBRUEHE": Tier 5 (Untrinkable Swill / Foul Plörre). Watered-down plörre, notoriously bad discount swill, spoiled/flat beer, or universally mocked cheap swill (e.g. Perlenbacher, Natty Light / Natural Light, Keystone Light, Milwaukee's Best, foul/flat beer).
                
                If the drink is NOT a beer (e.g. wine, coffee, tea, cocktail, soda, juice, water, energy drink, whiskey), set "beerVerdict": "NONE".

                Return a valid, standalone JSON object with the following fields:
                {
                  "name": "Exact drink name and edition/variant",
                  "category": "e.g. Red Wine, Craft IPA Beer, Single Origin Espresso, Cocktail, Matcha, Energy Drink, Soda, Sparkling Water, Kombucha, Bourbon",
                  "brandOrProducer": "Name of brand, brewery, winery, roaster or distiller",
                  "origin": "Country / Region of origin (e.g. Bordeaux, France or Kyoto, Japan)",
                  "abvOrCaffeine": "Alcohol by Volume (e.g. '13.5% ABV') or Caffeine content (e.g. '120mg caffeine') or 'Alcohol-Free'",
                  "description": "Engaging, sensory description of what this drink is, its heritage, and unique qualities (2-3 sentences)",
                  "beerVerdict": "HOPFENBOMBE | LECKER_BIERCHEN | WEGBIER | PENNERGLUECK | PISSBRUEHE | NONE",
                  "beerVerdictReason": "Short, humorous and punchy reason in German why this beer was awarded this specific tier (or null if NONE)",
                  "flavorProfile": {
                    "sweetnessLevel": 1-5,
                    "bitternessLevel": 1-5,
                    "acidityLevel": 1-5,
                    "body": "Light | Medium | Full | Crisp",
                    "aromas": ["aroma note 1", "aroma note 2", "aroma note 3"],
                    "tastingNotes": ["taste note 1", "taste note 2", "taste note 3"]
                  },
                  "nutrition": {
                    "estimatedCalories": "e.g. ~140 kcal per 330ml",
                    "estimatedSugar": "e.g. 0g or 24g",
                    "estimatedCarbs": "e.g. 3g",
                    "dietaryHighlights": ["e.g. Sugar Free", "Vegan", "Gluten Free", "Organic", "Zero Calorie"]
                  },
                  "servingRecommendations": {
                    "idealTemperature": "e.g. Ice Cold (3-5°C) or Cellar Temp (16-18°C) or Hot (85°C)",
                    "glassware": "e.g. Tulip Beer Glass, Bordeaux Wine Glass, Highball, Ceramic Mug",
                    "foodPairings": ["food pairing 1", "food pairing 2", "food pairing 3"],
                    "mixologyTipOrCocktail": "Optional cocktail recipe or serving garnish recommendation"
                  },
                  "interestingFacts": [
                    "Fascinating fact or trivia 1",
                    "Fascinating fact or trivia 2"
                  ],
                  "isIdentified": true
                }

                Important: 
                - Return ONLY the raw JSON object. Do not prefix with markdown explanations.
                - If the image is not a beverage, drink bottle, can, cup, or glass, set "isIdentified": false and give a helpful note in "description".
            """.trimIndent()

            val inputContent = content {
                image(scaledBitmap)
                text(promptText)
            }

            val response = generativeModel.generateContent(inputContent)
            val responseText = response.text ?: throw IllegalStateException("Empty response received from Gemini AI.")

            val cleanedJson = extractJsonFromResponse(responseText)
            val drinkDetails = try {
                jsonParser.decodeFromString<DrinkDetails>(cleanedJson)
            } catch (e: Exception) {
                // Fallback to manual parsing / partial structure
                DrinkDetails(
                    name = "Scanned Beverage",
                    description = responseText.replace("```json", "").replace("```", "").trim(),
                    isIdentified = true,
                    rawNotes = responseText
                )
            }

            Result.success(drinkDetails)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJsonFromResponse(text: String): String {
        val trimmed = text.trim()
        val startIndex = trimmed.indexOf('{')
        val lastIndex = trimmed.lastIndexOf('}')
        return if (startIndex != -1 && lastIndex != -1 && lastIndex > startIndex) {
            trimmed.substring(startIndex, lastIndex + 1)
        } else {
            trimmed
        }
    }
}
