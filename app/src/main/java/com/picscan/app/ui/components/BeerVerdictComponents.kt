package com.picscan.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.picscan.app.data.model.BeerVerdict
import com.picscan.app.data.model.DrinkDetails
import com.picscan.app.util.SoundEffectPlayer

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
 * Animated Celebration or Warning Banner displayed directly on DrinkResultScreen for the 5-Tier Beer Ranking.
 * Tuned for subtle, pleasant and non-intrusive motion.
 */
@Composable
fun BeerVerdictCard(
    drink: DrinkDetails,
    onShowFullAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val verdict = remember(drink) { drink.resolveBeerVerdict() }
    if (verdict == BeerVerdict.NONE) return

    val context = LocalContext.current
    val config = remember(verdict) { getVisualConfig(verdict) }

    // Subtle ambient breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "beerVerdictTransition")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.995f,
        targetValue = 1.008f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (verdict) {
                    BeerVerdict.HOPFENBOMBE -> 1500
                    BeerVerdict.LECKER_BIERCHEN -> 1800
                    BeerVerdict.WEGBIER -> 2000
                    BeerVerdict.PENNERGLUECK -> 1700
                    BeerVerdict.PISSBRUEHE -> 1400
                    BeerVerdict.NONE -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (verdict) {
                    BeerVerdict.HOPFENBOMBE -> 1600
                    BeerVerdict.PISSBRUEHE -> 1400
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashAlpha"
    )

    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = if (verdict == BeerVerdict.PISSBRUEHE || verdict == BeerVerdict.HOPFENBOMBE) -0.8f else 0f,
        targetValue = if (verdict == BeerVerdict.PISSBRUEHE || verdict == BeerVerdict.HOPFENBOMBE) 0.8f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeOffset"
    )

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            config.primaryColor.copy(alpha = flashAlpha),
            config.secondaryColor.copy(alpha = flashAlpha),
            config.primaryColor.copy(alpha = flashAlpha)
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .offset(x = shakeOffset.dp)
            .clickable {
                SoundEffectPlayer.playBeerVerdictSound(verdict, context)
                onShowFullAlert()
            },
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

            Text(
                text = drink.beerVerdictReason ?: verdict.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        SoundEffectPlayer.playBeerVerdictSound(verdict, context)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = config.buttonColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Play Sound Effect",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sound abspielen", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShowFullAlert,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Animation",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Animation")
                }
            }
        }
    }
}

/**
 * Fullscreen Interactive Dialog with gentle atmospheric animations for 5-Tier Beer Ranking.
 */
@Composable
fun BeerVerdictCelebrationDialog(
    drink: DrinkDetails,
    onDismiss: () -> Unit
) {
    val verdict = remember(drink) { drink.resolveBeerVerdict() }
    if (verdict == BeerVerdict.NONE) return

    val context = LocalContext.current
    val config = remember(verdict) { getVisualConfig(verdict) }

    // Play sound automatically when dialog opens
    LaunchedEffect(verdict) {
        SoundEffectPlayer.playBeerVerdictSound(verdict, context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fullscreenBeerAlert")

    // Gentle atmospheric background glow
    val strobeColor1 by infiniteTransition.animateColor(
        initialValue = config.primaryColor.copy(alpha = 0.70f),
        targetValue = config.secondaryColor.copy(alpha = 0.70f),
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (verdict) {
                    BeerVerdict.HOPFENBOMBE -> 1600
                    BeerVerdict.PISSBRUEHE -> 1500
                    else -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobeColor1"
    )

    val strobeColor2 by infiniteTransition.animateColor(
        initialValue = Color.Black.copy(alpha = 0.95f),
        targetValue = config.containerBgColor.copy(alpha = 0.95f),
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (verdict) {
                    BeerVerdict.HOPFENBOMBE -> 1600
                    BeerVerdict.PISSBRUEHE -> 1500
                    else -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strobeColor2"
    )

    // Subtle gentle breathing pulse
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (verdict) {
                    BeerVerdict.HOPFENBOMBE -> 1600
                    BeerVerdict.PISSBRUEHE -> 1400
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scalePulse"
    )

    // Subtle gentle wobble tilt
    val rotationWobble by infiniteTransition.animateFloat(
        initialValue = when (verdict) {
            BeerVerdict.PISSBRUEHE -> -1.5f
            BeerVerdict.HOPFENBOMBE -> -1.2f
            BeerVerdict.PENNERGLUECK -> -1.0f
            else -> -0.6f
        },
        targetValue = when (verdict) {
            BeerVerdict.PISSBRUEHE -> 1.5f
            BeerVerdict.HOPFENBOMBE -> 1.2f
            BeerVerdict.PENNERGLUECK -> 1.0f
            else -> 0.6f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (verdict) {
                    BeerVerdict.HOPFENBOMBE -> 1400
                    BeerVerdict.PISSBRUEHE -> 1200
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationWobble"
    )

    val shakeX by infiniteTransition.animateFloat(
        initialValue = if (verdict == BeerVerdict.PISSBRUEHE) -1.2f else 0f,
        targetValue = if (verdict == BeerVerdict.PISSBRUEHE) 1.2f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeX"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(strobeColor1, strobeColor2)
                    )
                )
                .clickable { onDismiss() }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Floating Decorative Emoticons matching tier
            VerdictFloatingParticles(verdict = verdict)

            // Main Alert Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scalePulse)
                    .offset(x = shakeX.dp)
                    .rotate(rotationWobble)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black.copy(alpha = 0.90f))
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.8f), config.primaryColor, config.secondaryColor, Color.White.copy(alpha = 0.8f))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tier Header
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = config.buttonColor.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = config.tierLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = config.textColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                // Header icons
                Text(
                    text = config.bannerEmojis,
                    fontSize = 24.sp
                )

                // Title
                Text(
                    text = verdict.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = config.textColor,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.5.sp
                )

                // Drink Name
                Text(
                    text = drink.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // Description Reason
                Text(
                    text = drink.beerVerdictReason ?: verdict.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons inside dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            SoundEffectPlayer.playBeerVerdictSound(verdict, context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = config.buttonColor
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nochmal Sound!", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = when (verdict) {
                                BeerVerdict.HOPFENBOMBE -> "Explosion! 💣"
                                BeerVerdict.LECKER_BIERCHEN -> "Prost! 🍻"
                                BeerVerdict.WEGBIER -> "Weiterlaufen! 🚶‍♂️"
                                BeerVerdict.PENNERGLUECK -> "Ex und hopp! 🥫"
                                BeerVerdict.PISSBRUEHE -> "Weg damit! 🗑️"
                                BeerVerdict.NONE -> "OK"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerdictFloatingParticles(verdict: BeerVerdict) {
    val infiniteTransition = rememberInfiniteTransition(label = "verdictParticles")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (verdict == BeerVerdict.HOPFENBOMBE || verdict == BeerVerdict.PISSBRUEHE) 1600 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particleAlpha"
    )

    val particles = remember(verdict) {
        when (verdict) {
            BeerVerdict.HOPFENBOMBE -> listOf("💣", "💥", "🌿", "🚀", "✨", "🔥")
            BeerVerdict.LECKER_BIERCHEN -> listOf("🍻", "✨", "🍺", "👑", "⭐", "🎉")
            BeerVerdict.WEGBIER -> listOf("🚶‍♂️", "🍺", "👟", "🏙️", "🎶", "🌿")
            BeerVerdict.PENNERGLUECK -> listOf("🥫", "🥴", "🪙", "🛒", "💨", "🍺")
            BeerVerdict.PISSBRUEHE -> listOf("☣️", "🤢", "🝽", "⚠️", "❌", "🤮")
            BeerVerdict.NONE -> emptyList()
        }
    }

    if (particles.size >= 6) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(particles[0], fontSize = 28.sp, modifier = Modifier.align(Alignment.TopStart).padding(30.dp).scale(0.9f + alpha * 0.15f))
            Text(particles[1], fontSize = 30.sp, modifier = Modifier.align(Alignment.TopEnd).padding(40.dp).scale(0.9f + alpha * 0.15f))
            Text(particles[2], fontSize = 26.sp, modifier = Modifier.align(Alignment.BottomStart).padding(50.dp).scale(0.9f + alpha * 0.15f))
            Text(particles[3], fontSize = 30.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(30.dp).scale(0.9f + alpha * 0.15f))
            Text(particles[4], fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterStart).padding(20.dp).scale(0.9f + alpha * 0.15f))
            Text(particles[5], fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(20.dp).scale(0.9f + alpha * 0.15f))
        }
    }
}
