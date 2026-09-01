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
                IllegalStateException("Gemini-API-Schlüssel fehlt. Bitte trage deinen API-Schlüssel in den Einstellungen ein.")
            )
        }

        try {
            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )

            val scaledBitmap = ImageUtils.scaleBitmapDown(bitmap)

            val promptText = """
                Du bist ein weltklasse Sommelier, Barista, Mixologe und Bier-Kenner.
                Analysiere das Getränk, die Flasche, Dose, das Glas oder Etikett auf diesem Bild.
                
                SPRACHVORGABE (SEHR WICHTIG):
                - ALLE Ausgabetexte, Beschreibungen, Kategorienamen, Nährwertangaben, Aromen, Geschmacksnoten, Speisenempfehlungen und Fakten MÜSSEN VOLLSTÄNDIG AUF DEUTSCH verfasst sein.
                
                BIER-BEWERTUNGS-ANWEISUNG (5-Stufen-Ranking):
                Falls das Getränk auf dem Bild ein BIER (oder Biermischgetränk/Cider) ist, ordne es genau einer dieser 5 Stufen zu:
                1. "HOPFENBOMBE": Stufe 1 (Meisterwerk / Craft-Explosion). Intensive hopfenbetonte Craft-Biere, Double/Triple IPAs, Imperial Stouts, Trappistenbiere, edle Microbrew-Kreationen (z. B. BrewDog Punk IPA, Tree House, Westvleteren, Sierra Nevada Torpedo, besondere Craft-Brauereien).
                2. "LECKER_BIERCHEN": Stufe 2 (Hohe Braukunst / Qualitäts-Klassiker). Hervorragende, traditionsreiche bayerische, belgische oder deutsche Traditionsbiere (z. B. Augustiner, Paulaner, Tegernseer, Weihenstephaner, Erdinger, Rothaus Tannenzäpfle, Chiemseer, Bayreuther, Guinness, Pilsner Urquell, Duvel, Leffe, Ayinger).
                3. "WEGBIER": Stufe 3 (Kiosk- & Späti-Held). Solide, süffige Alltags-Lager/Pilsener, Kiosk- und Festival-Klassiker (z. B. Astra, Sternburg, Krombacher, Bitburger, Beck's, Jever, Warsteiner, Veltins, Heineken, Flensburger, Berliner Kindl, Stauder).
                4. "PENNERGLUECK": Stufe 4 (Sparfuchs-Dosenkracher). Günstige Discounter-Dosenbiere, Sparfuchs-Kultbiere (z. B. Oettinger, 5,0 Original, Hansa Pils, Paderborner, Karlskrone, Turmbräu, Schultenbräu, Adelskronen, Meisterbräu).
                5. "PISSBRUEHE": Stufe 5 (Untrinkbare Plörre / Notstand). Wässrige Plörre, berüchtigt schlechtes Billigstbier oder abgestandenes Bier (z. B. Perlenbacher, Natty Light / Natural Light, Keystone Light, schales/abgestandenes Bier).
                
                Falls es KEIN Bier ist (z. B. Wein, Kaffee, Tee, Cocktail, Limonade, Saft, Wasser, Energy-Drink, Spirituose), setze "beerVerdict": "NONE" und "beerVerdictReason": null.

                Gib ein valides, eigenständiges JSON-Objekt mit exakt diesen Feldern auf DEUTSCH zurück:
                {
                  "name": "Genauer Getränkename und Sorte/Edition",
                  "category": "z. B. Rotwein, Weißwein, Craft-Bier / IPA, Pils, Helles, Espresso, Cocktail, Matcha, Energy-Drink, Limonade, Mineralwasser, Kombucha, Whiskey, Gin",
                  "brandOrProducer": "Marke, Brauerei, Weingut, Rösterei oder Destillerie",
                  "origin": "Herkunftsland / Region (z. B. Bordeaux, Frankreich oder Bayern, Deutschland oder Kyoto, Japan)",
                  "abvOrCaffeine": "Alkoholgehalt (z. B. '5,2 % vol.') oder Koffeingehalt (z. B. '120 mg Koffein') oder 'Alkoholfrei'",
                  "description": "Ansprechende, sensorische Beschreibung auf Deutsch über Geschmack, Herkunft und Besonderheiten (2-3 Sätze)",
                  "beerVerdict": "HOPFENBOMBE | LECKER_BIERCHEN | WEGBIER | PENNERGLUECK | PISSBRUEHE | NONE",
                  "beerVerdictReason": "Kurze, humorvolle und treffende Begründung auf Deutsch für die Einstufung (oder null falls NONE)",
                  "flavorProfile": {
                    "sweetnessLevel": 1-5,
                    "bitternessLevel": 1-5,
                    "acidityLevel": 1-5,
                    "body": "Leicht | Mittel | Vollmundig | Spritzig",
                    "aromas": ["Aroma-Note 1 auf Deutsch", "Aroma-Note 2 auf Deutsch", "Aroma-Note 3 auf Deutsch"],
                    "tastingNotes": ["Geschmacksnote 1 auf Deutsch", "Geschmacksnote 2 auf Deutsch", "Geschmacksnote 3 auf Deutsch"]
                  },
                  "nutrition": {
                    "estimatedCalories": "z. B. ~140 kcal pro 330 ml",
                    "estimatedSugar": "z. B. 0 g oder 24 g",
                    "estimatedCarbs": "z. B. 3 g",
                    "dietaryHighlights": ["z. B. Zuckerfrei", "Vegan", "Glutenfrei", "Bio", "Kalorienarm"]
                  },
                  "servingRecommendations": {
                    "idealTemperature": "z. B. Eiskalt (3-5 °C) oder Gekühlt (6-8 °C) oder Kellertemperatur (14-16 °C) oder Heiß (85 °C)",
                    "glassware": "z. B. Tulpenglas, Bordeaux-Glas, Highball-Glas, Steingutkrug, Keramiktasse",
                    "foodPairings": ["Speiseempfehlung 1 auf Deutsch", "Speiseempfehlung 2 auf Deutsch", "Speiseempfehlung 3 auf Deutsch"],
                    "mixologyTipOrCocktail": "Serviertipp, Garnitur oder Rezeptempfehlung auf Deutsch"
                  },
                  "interestingFacts": [
                    "Spannender Fakt oder Wissenswertes 1 auf Deutsch",
                    "Spannender Fakt oder Wissenswertes 2 auf Deutsch"
                  ],
                  "isIdentified": true
                }

                Wichtig:
                - Gib AUSSCHLIESSLICH das reine JSON-Objekt ohne umschließenden Text zurück.
                - Alle Texte, Bezeichnungen und Beschreibungen MÜSSEN in deutscher Sprache sein.
                - Wenn das Bild kein Getränk zeigt, setze "isIdentified": false und gib einen freundlichen Hinweis auf Deutsch in "description".
            """.trimIndent()

            val inputContent = content {
                image(scaledBitmap)
                text(promptText)
            }

            val response = generativeModel.generateContent(inputContent)
            val responseText = response.text ?: throw IllegalStateException("Keine Antwort von Gemini AI erhalten.")

            val cleanedJson = extractJsonFromResponse(responseText)
            val drinkDetails = try {
                jsonParser.decodeFromString<DrinkDetails>(cleanedJson)
            } catch (e: Exception) {
                // Fallback to manual parsing / partial structure
                DrinkDetails(
                    name = "Gescanntes Getränk",
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
