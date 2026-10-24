package com.picscan.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picscan.app.data.model.BeerVerdict
import com.picscan.app.data.model.DrinkDetails

private data class VerdictVisualConfig(
    val primaryColor: Color,
    val secondaryColor: Color,
    val containerBgColor: Color,
    val textColor: Color,
    val buttonColor: Color,
    val tierLabel: String,
    val leftEmoji: String,
    val rightEmoji: String,
    val bannerEmojis: String
)

private fun getVisualConfig(verdict: BeerVerdict): VerdictVisualConfig {
    return when (verdict) {
        BeerVerdict.HOPFENBOMBE -> VerdictVisualConfig(
            primaryColor = Color(0xFF00E676),
            secondaryColor = Color(0xFFFFD600),
            containerBgColor = Color(0xFF052B14),
            textColor = Color(0xFF69F0AE),
            buttonColor = Color(0xFF00C853),
            tierLabel = "Rang 1/5 • Ultimative Hopfenbombe",
            leftEmoji = "💣",
            rightEmoji = "💥",
            bannerEmojis = "💣 💥 🌿 🚀 💥 💣"
        )
        BeerVerdict.LECKER_BIERCHEN -> VerdictVisualConfig(
            primaryColor = Color(0xFFFFB300),
            secondaryColor = Color(0xFFFF6F00),
            containerBgColor = Color(0xFF2E1C00),
            textColor = Color(0xFFFFD54F),
            buttonColor = Color(0xFFFF8F00),
            tierLabel = "Rang 2/5 • Hohe Braukunst",
            leftEmoji = "🍻",
            rightEmoji = "✨",
            bannerEmojis = "🍻 ✨ 🍺 👑 ✨ 🍻"
        )
        BeerVerdict.WEGBIER -> VerdictVisualConfig(
            primaryColor = Color(0xFF00B0FF),
            secondaryColor = Color(0xFF00E5FF),
            containerBgColor = Color(0xFF002233),
            textColor = Color(0xFF80D8FF),
            buttonColor = Color(0xFF0288D1),
            tierLabel = "Rang 3/5 • Kiosk & Späti Held",
            leftEmoji = "🚶‍♂️",
            rightEmoji = "🍺",
            bannerEmojis = "🚶‍♂️ 🍺 🏙️ 🎶 🍺 🚶‍♂️"
        )
        BeerVerdict.PENNERGLUECK -> VerdictVisualConfig(
            primaryColor = Color(0xFFFFAB00),
            secondaryColor = Color(0xFF8D6E63),
            containerBgColor = Color(0xFF2B1F17),
            textColor = Color(0xFFFFD180),
            buttonColor = Color(0xFF795548),
            tierLabel = "Rang 4/5 • Sparfuchs-Dosenkracher",
            leftEmoji = "🥫",
            rightEmoji = "🥴",
            bannerEmojis = "🥫 🥴 🪙 🛒 🥫"
        )
        BeerVerdict.PISSBRUEHE -> VerdictVisualConfig(
            primaryColor = Color(0xFFFF1744),
            secondaryColor = Color(0xFFAEEA00),
            containerBgColor = Color(0xFF2A0800),
            textColor = Color(0xFFFF5252),
            buttonColor = Color(0xFFD32F2F),
            tierLabel = "Rang 5/5 • Untrinkbare Plörre",
            leftEmoji = "☣️",
            rightEmoji = "🤢",
            bannerEmojis = "☣️ 🤢 🝽 ⚠️ ❌"
        )
        BeerVerdict.NONE -> VerdictVisualConfig(
            primaryColor = Color.Gray,
            secondaryColor = Color.LightGray,
            containerBgColor = Color.DarkGray,
            textColor = Color.White,
            buttonColor = Color.Gray,
            tierLabel = "",
            leftEmoji = "",
            rightEmoji = "",
            bannerEmojis = ""
        )
    }
}

/**
 * Banner displayed directly on DrinkResultScreen for the 5-Tier Beer Ranking.
 */
@Composable
fun BeerVerdictCard(
    drink: DrinkDetails,
    modifier: Modifier = Modifier
) {
    val verdict = remember(drink) { drink.resolveBeerVerdict() }
    if (verdict == BeerVerdict.NONE) return

    val config = remember(verdict) { getVisualConfig(verdict) }

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            config.primaryColor,
            config.secondaryColor,
            config.primaryColor
        )
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = config.containerBgColor
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = cardBrush,
            width = 2.dp
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tier Badge Tag
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = config.buttonColor.copy(alpha = 0.35f)
            ) {
                Text(
                    text = config.tierLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = config.textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = config.leftEmoji,
                    fontSize = 26.sp
                )
                Text(
                    text = verdict.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = config.textColor
                )
                Text(
                    text = config.rightEmoji,
                    fontSize = 26.sp
                )
            }

            if (!drink.origin.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.25f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Herkunft",
                            tint = config.textColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = drink.origin,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
            }

            Text(
                text = drink.beerVerdictReason ?: verdict.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
