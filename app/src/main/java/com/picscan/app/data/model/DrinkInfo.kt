package com.picscan.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BeerVerdict(
    val title: String,
    val emoji: String,
    val tierNumber: Int,
    val subtitle: String
) {
    @SerialName("HOPFENBOMBE")
    HOPFENBOMBE(
        title = "HOPFENBOMBE!",
        emoji = "💣💥",
        tierNumber = 1,
        subtitle = "Göttliches Meisterwerk & absolute Geschmacksexplosion"
    ),

    @SerialName("LECKER_BIERCHEN")
    LECKER_BIERCHEN(
        title = "LECKER BIERCHEN!",
        emoji = "🍻✨",
        tierNumber = 2,
        subtitle = "Hervorragendes Qualitätsbier von höchster Güte"
    ),

    @SerialName("WEGBIER")
    WEGBIER(
        title = "WEGBIER!",
        emoji = "🚶‍♂️🍺",
        tierNumber = 3,
        subtitle = "Solider Alltagsbegleiter & ehrlicher Kiosk-Klassiker"
    ),

    @SerialName("PENNERGLUECK")
    PENNERGLUECK(
        title = "PENNERGLÜCK!",
        emoji = "🥫🥴",
        tierNumber = 4,
        subtitle = "Kultiger Billig-Dosenkracher für den schmalen Taler"
    ),

    @SerialName("PISSBRUEHE")
    PISSBRUEHE(
        title = "PISSBRÜHE!",
        emoji = "🤢🚽",
        tierNumber = 5,
        subtitle = "Absoluter Notstand – wässrige, untrinkbare Plörre"
    ),

    @SerialName("NONE")
    NONE(
        title = "",
        emoji = "",
        tierNumber = 0,
        subtitle = ""
    )
}

@Serializable
data class DrinkDetails(
    val name: String,
    val category: String = "Beverage",
    val brandOrProducer: String? = null,
    val origin: String? = null,
    val abvOrCaffeine: String? = null,
    val description: String = "",
    val flavorProfile: FlavorProfile = FlavorProfile(),
    val nutrition: NutritionalInfo = NutritionalInfo(),
    val servingRecommendations: ServingRecommendation = ServingRecommendation(),
    val interestingFacts: List<String> = emptyList(),
    val isIdentified: Boolean = true,
    val beerVerdict: BeerVerdict = BeerVerdict.NONE,
    val beerVerdictReason: String? = null,
    val rawNotes: String? = null
) {
    /**
     * Resolves the 5-tier beer verdict taking into account Gemini output or intelligent fallback heuristics.
     */
    fun resolveBeerVerdict(): BeerVerdict {
        if (beerVerdict != BeerVerdict.NONE) {
            return beerVerdict
        }

        val allText = listOfNotNull(name, category, brandOrProducer, description).joinToString(" ").lowercase()
        val isBeer = category.contains("Beer", ignoreCase = true) ||
                category.contains("Ale", ignoreCase = true) ||
                category.contains("IPA", ignoreCase = true) ||
                category.contains("Pils", ignoreCase = true) ||
                category.contains("Lager", ignoreCase = true) ||
                category.contains("Weizen", ignoreCase = true) ||
                category.contains("Helles", ignoreCase = true) ||
                category.contains("Stout", ignoreCase = true) ||
                category.contains("Kölsch", ignoreCase = true) ||
                category.contains("Bier", ignoreCase = true) ||
                category.contains("Porter", ignoreCase = true) ||
                category.contains("Gose", ignoreCase = true) ||
                category.contains("Bock", ignoreCase = true) ||
                allText.contains("bier") ||
                allText.contains("brewery") ||
                allText.contains("brauerei")

        if (!isBeer) return BeerVerdict.NONE

        // 1. Tier 1: Hopfenbombe (Specialty craft, DIPA, Imperial Stout, West Coast, Trappist masterpieces)
        val hopfenbombeKeywords = listOf(
            "hopfenbombe", "double ipa", "triple ipa", "dipa", "imperial ipa", "neipa",
            "new england ipa", "hazy ipa", "imperial stout", "barrel aged", "west coast ipa",
            "trappist", "westvleteren", "cantillon", "3 fonteinen", "tree house", "trillium",
            "other half", "pliny the elder", "stone brewing", "omnipollo", "brewdog punk ipa",
            "dry hopped", "kalthopfung", "gehopft", "craft ipa", "craft beer"
        )
        for (keyword in hopfenbombeKeywords) {
            if (allText.contains(keyword)) {
                return BeerVerdict.HOPFENBOMBE
            }
        }

        // 5. Tier 5: Pissbrühe (Reputable swill, universally mocked, flat/foul, bottom barrel)
        val pissbrueheKeywords = listOf(
            "pissbrühe", "pissbruehe", "plörre", "ploerre", "perlenbacher",
            "natty light", "natural light", "natural ice", "milwaukee's best",
            "keystone light", "busch light", "falkenfelser", "grafenwalder"
        )
        for (keyword in pissbrueheKeywords) {
            if (allText.contains(keyword)) {
                return BeerVerdict.PISSBRUEHE
            }
        }

        // 4. Tier 4: Pennerglück (Classic cheap discount & tin-can cult beers)
        val pennerglueckKeywords = listOf(
            "pennerglück", "pennerglueck", "oettinger", "5,0 original", "5.0 original",
            "5,0 bier", "5.0 bier", "paderborner", "hansa pils", "hansa export",
            "turmbräu", "turmbraeu", "karlskrone", "adelskronen", "schultenbräu",
            "schultenbraeu", "meisterbräu", "meisterbraeu", "ratskrone", "landfürst",
            "billigbier", "dosensaufen"
        )
        for (keyword in pennerglueckKeywords) {
            if (allText.contains(keyword)) {
                return BeerVerdict.PENNERGLUECK
            }
        }

        // 3. Tier 3: Wegbier (Späti & kiosk classics, session beers, travel companions)
        val wegbierKeywords = listOf(
            "wegbier", "astra", "sternburg", "sterni", "berliner kindl", "berliner pilsner",
            "krombacher", "bitburger", "becks", "beck's", "warsteiner", "veltins",
            "jever", "heineken", "holsten", "flensburger", "stella artois", "tuborg",
            "hasseröder", "hasseroeder", "stauder", "braustolz", "urgewürz", "wicküler"
        )
        for (keyword in wegbierKeywords) {
            if (allText.contains(keyword)) {
                return BeerVerdict.WEGBIER
            }
        }

        // 2. Tier 2: Lecker Bierchen (High-quality Bavarian/German/European traditional classics)
        val leckerBierchenKeywords = listOf(
            "augustiner", "tegernseer", "weihenstephaner", "rothaus", "paulaner",
            "erdinger", "franziskaner", "chiemseer", "bayreuther", "schneider weisse",
            "ayinger", "gösser", "goesser", "hacker-pschorr", "löwenbräu", "loewenbraeu",
            "spaten", "pilsner urquell", "budvar", "budweiser budvar", "guinness",
            "corona", "peroni", "duvel", "chimay", "leffe", "hoegaarden",
            "früh", "gaffel", "reissdorf", "sierra nevada", "helles", "weizen", "lager"
        )
        for (keyword in leckerBierchenKeywords) {
            if (allText.contains(keyword)) {
                return BeerVerdict.LECKER_BIERCHEN
            }
        }

        // Default fallback for recognized beers
        return BeerVerdict.LECKER_BIERCHEN
    }
}

@Serializable
data class FlavorProfile(
    val sweetnessLevel: Int = 3, // 1 to 5
    val bitternessLevel: Int = 3, // 1 to 5
    val acidityLevel: Int = 3, // 1 to 5
    val body: String = "Medium", // Light, Medium, Full
    val aromas: List<String> = emptyList(),
    val tastingNotes: List<String> = emptyList()
)

@Serializable
data class NutritionalInfo(
    val estimatedCalories: String = "N/A",
    val estimatedSugar: String = "N/A",
    val estimatedCarbs: String = "N/A",
    val dietaryHighlights: List<String> = emptyList() // e.g. "Gluten-Free", "Vegan", "Zero Sugar", "Organic"
)

@Serializable
data class ServingRecommendation(
    val idealTemperature: String = "Chilled (4-7°C / 40-45°F)",
    val glassware: String = "Standard Glass",
    val foodPairings: List<String> = emptyList(),
    val mixologyTipOrCocktail: String? = null
)

@Serializable
data class ScanHistoryItem(
    val id: String,
    val drink: DrinkDetails,
    val timestamp: Long,
    val imagePath: String? = null
)
