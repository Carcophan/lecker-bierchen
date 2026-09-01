package com.picscan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picscan.app.ui.theme.*

@Composable
fun DrinkCategoryBadge(category: String, modifier: Modifier = Modifier) {
    val (bgColor, icon) = when {
        category.contains("Wine", ignoreCase = true) -> AccentWine to Icons.Default.LocalBar
        category.contains("Beer", ignoreCase = true) || category.contains("Ale", ignoreCase = true) || category.contains("IPA", ignoreCase = true) -> AccentBeer to Icons.Default.SportsBar
        category.contains("Coffee", ignoreCase = true) || category.contains("Espresso", ignoreCase = true) -> AccentCoffee to Icons.Default.Coffee
        category.contains("Tea", ignoreCase = true) || category.contains("Matcha", ignoreCase = true) -> AccentTea to Icons.Default.EmojiFoodBeverage
        category.contains("Cocktail", ignoreCase = true) || category.contains("Spirit", ignoreCase = true) || category.contains("Whiskey", ignoreCase = true) -> AccentCocktail to Icons.Default.Liquor
        category.contains("Energy", ignoreCase = true) -> AccentEnergy to Icons.Default.Bolt
        else -> AmberPrimary to Icons.Default.LocalDrink
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor.copy(alpha = 0.15f),
        border = null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = bgColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                color = bgColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LevelIndicator(
    label: String,
    level: Int, // 1 to 5
    modifier: Modifier = Modifier,
    activeColor: Color = AmberPrimary
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..5) {
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (i <= level) activeColor else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
fun InfoChip(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.Medium
        )
    }
}
