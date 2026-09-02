package com.picscan.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BeerListType(
    val title: String,
    val subtitle: String,
    val emoji: String
) {
    @SerialName("KNOWN")
    KNOWN(
        title = "Kenne ich",
        subtitle = "Schon getrunken & bewertet",
        emoji = "🍺"
    ),

    @SerialName("WISHLIST")
    WISHLIST(
        title = "Will ich",
        subtitle = "Auf der Wunschliste / Merkliste",
        emoji = "📌"
    );

    companion object {
        fun fromString(value: String?): BeerListType {
            return when (value?.uppercase()) {
                "WISHLIST", "WILL_ICH", "WISH" -> WISHLIST
                else -> KNOWN
            }
        }
    }
}

@Serializable
data class SavedBeerItem(
    val id: String = "",
    val name: String = "",
    val category: String = "Bier",
    val brandOrProducer: String? = null,
    val origin: String? = null,
    val abvOrCaffeine: String? = null,
    val description: String = "",
    val beerVerdict: BeerVerdict = BeerVerdict.NONE,
    val beerVerdictReason: String? = null,
    val listType: BeerListType = BeerListType.KNOWN,
    val rating: Float = 0f,
    val userNotes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val imageUrl: String? = null,
    val imageBase64: String? = null,
    val sweetnessLevel: Int = 3,
    val bitternessLevel: Int = 3,
    val acidityLevel: Int = 3,
    val body: String = "Mittel",
    val aromas: List<String> = emptyList(),
    val tastingNotes: List<String> = emptyList(),
    val glassware: String = "Standardglas",
    val idealTemperature: String = "Gekühlt (4-7 °C)",
    val foodPairings: List<String> = emptyList()
) {
    fun toDrinkDetails(): DrinkDetails {
        return DrinkDetails(
            name = name,
            category = category,
            brandOrProducer = brandOrProducer,
            origin = origin,
            abvOrCaffeine = abvOrCaffeine,
            description = description,
            beerVerdict = beerVerdict,
            beerVerdictReason = beerVerdictReason,
            flavorProfile = FlavorProfile(
                sweetnessLevel = sweetnessLevel,
                bitternessLevel = bitternessLevel,
                acidityLevel = acidityLevel,
                body = body,
                aromas = aromas,
                tastingNotes = tastingNotes
            ),
            servingRecommendations = ServingRecommendation(
                glassware = glassware,
                idealTemperature = idealTemperature,
                foodPairings = foodPairings
            )
        )
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "category" to category,
            "brandOrProducer" to brandOrProducer,
            "origin" to origin,
            "abvOrCaffeine" to abvOrCaffeine,
            "description" to description,
            "beerVerdict" to beerVerdict.name,
            "beerVerdictReason" to beerVerdictReason,
            "listType" to listType.name,
            "rating" to rating.toDouble(),
            "userNotes" to userNotes,
            "timestamp" to timestamp,
            "imagePath" to imagePath,
            "imageUrl" to imageUrl,
            "imageBase64" to imageBase64,
            "sweetnessLevel" to sweetnessLevel,
            "bitternessLevel" to bitternessLevel,
            "acidityLevel" to acidityLevel,
            "body" to body,
            "aromas" to aromas,
            "tastingNotes" to tastingNotes,
            "glassware" to glassware,
            "idealTemperature" to idealTemperature,
            "foodPairings" to foodPairings
        )
    }

    companion object {
        fun fromDrinkDetails(
            id: String,
            drink: DrinkDetails,
            listType: BeerListType,
            imagePath: String? = null,
            imageUrl: String? = null,
            imageBase64: String? = null,
            rating: Float = 0f,
            userNotes: String = "",
            timestamp: Long = System.currentTimeMillis()
        ): SavedBeerItem {
            val resolvedVerdict = drink.resolveBeerVerdict()
            return SavedBeerItem(
                id = id,
                name = drink.name,
                category = drink.category,
                brandOrProducer = drink.brandOrProducer,
                origin = drink.origin,
                abvOrCaffeine = drink.abvOrCaffeine,
                description = drink.description,
                beerVerdict = resolvedVerdict,
                beerVerdictReason = drink.beerVerdictReason ?: resolvedVerdict.subtitle,
                listType = listType,
                rating = rating,
                userNotes = userNotes,
                timestamp = timestamp,
                imagePath = imagePath,
                imageUrl = imageUrl,
                imageBase64 = imageBase64,
                sweetnessLevel = drink.flavorProfile.sweetnessLevel,
                bitternessLevel = drink.flavorProfile.bitternessLevel,
                acidityLevel = drink.flavorProfile.acidityLevel,
                body = drink.flavorProfile.body,
                aromas = drink.flavorProfile.aromas,
                tastingNotes = drink.flavorProfile.tastingNotes,
                glassware = drink.servingRecommendations.glassware,
                idealTemperature = drink.servingRecommendations.idealTemperature,
                foodPairings = drink.servingRecommendations.foodPairings
            )
        }

        fun fromMap(id: String, map: Map<String, Any?>): SavedBeerItem {
            val nestedDrink = map["drink"] as? Map<*, *>

            val name = (map["name"] as? String)
                ?: (map["title"] as? String)
                ?: (map["drinkName"] as? String)
                ?: (nestedDrink?.get("name") as? String)
                ?: ""

            val category = (map["category"] as? String)
                ?: (map["type"] as? String)
                ?: (nestedDrink?.get("category") as? String)
                ?: "Bier"

            val brandOrProducer = (map["brandOrProducer"] as? String)
                ?: (map["brand"] as? String)
                ?: (map["producer"] as? String)
                ?: (nestedDrink?.get("brandOrProducer") as? String)

            val origin = (map["origin"] as? String)
                ?: (nestedDrink?.get("origin") as? String)

            val abvOrCaffeine = (map["abvOrCaffeine"] as? String)
                ?: (map["abv"] as? String)
                ?: (nestedDrink?.get("abvOrCaffeine") as? String)

            val description = (map["description"] as? String)
                ?: (nestedDrink?.get("description") as? String)
                ?: ""

            val verdictString = (map["beerVerdict"] as? String)
                ?: (map["verdict"] as? String)
                ?: (nestedDrink?.get("beerVerdict") as? String)
                ?: ""

            val verdict = try {
                if (verdictString.isNotBlank()) BeerVerdict.valueOf(verdictString.uppercase()) else BeerVerdict.NONE
            } catch (_: Exception) {
                BeerVerdict.NONE
            }

            val listTypeString = (map["listType"] as? String)
                ?: (map["status"] as? String)
                ?: (map["list"] as? String)

            val listType = BeerListType.fromString(listTypeString)

            val rating = ((map["rating"] as? Number)?.toFloat())
                ?: ((map["score"] as? Number)?.toFloat())
                ?: ((map["stars"] as? Number)?.toFloat())
                ?: 0f

            val userNotes = (map["userNotes"] as? String)
                ?: (map["notes"] as? String)
                ?: (map["comment"] as? String)
                ?: ""

            val timestamp = ((map["timestamp"] as? Number)?.toLong())
                ?: ((map["createdAt"] as? Number)?.toLong())
                ?: System.currentTimeMillis()

            val imagePath = (map["imagePath"] as? String)
                ?: (map["image"] as? String)?.takeIf { !it.length.let { len -> len > 500 } && !it.startsWith("/9j/") && !it.startsWith("data:") }

            val imageUrl = (map["imageUrl"] as? String)
                ?: (map["url"] as? String)

            val imageBase64 = (map["imageBase64"] as? String)
                ?: (map["imageData"] as? String)
                ?: (map["image"] as? String)?.takeIf { it.length > 500 || it.startsWith("/9j/") || it.startsWith("data:") }

            val aromasList = (map["aromas"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: (nestedDrink?.get("aromas") as? List<*>)?.mapNotNull { it?.toString() }
                ?: emptyList()

            val tastingNotesList = (map["tastingNotes"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: (nestedDrink?.get("tastingNotes") as? List<*>)?.mapNotNull { it?.toString() }
                ?: emptyList()

            val foodPairingsList = (map["foodPairings"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: (nestedDrink?.get("foodPairings") as? List<*>)?.mapNotNull { it?.toString() }
                ?: emptyList()

            return SavedBeerItem(
                id = id,
                name = name,
                category = category,
                brandOrProducer = brandOrProducer,
                origin = origin,
                abvOrCaffeine = abvOrCaffeine,
                description = description,
                beerVerdict = verdict,
                beerVerdictReason = (map["beerVerdictReason"] as? String) ?: (nestedDrink?.get("beerVerdictReason") as? String),
                listType = listType,
                rating = rating,
                userNotes = userNotes,
                timestamp = timestamp,
                imagePath = imagePath,
                imageUrl = imageUrl,
                imageBase64 = imageBase64,
                sweetnessLevel = ((map["sweetnessLevel"] as? Number)?.toInt()) ?: 3,
                bitternessLevel = ((map["bitternessLevel"] as? Number)?.toInt()) ?: 3,
                acidityLevel = ((map["acidityLevel"] as? Number)?.toInt()) ?: 3,
                body = (map["body"] as? String) ?: "Mittel",
                aromas = aromasList,
                tastingNotes = tastingNotesList,
                glassware = (map["glassware"] as? String) ?: "Standardglas",
                idealTemperature = (map["idealTemperature"] as? String) ?: "Gekühlt (4-7 °C)",
                foodPairings = foodPairingsList
            )
        }
    }
}
