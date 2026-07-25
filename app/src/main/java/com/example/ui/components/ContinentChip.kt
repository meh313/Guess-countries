package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SouthAmerica
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AfricaColor
import com.example.ui.theme.AmericasColor
import com.example.ui.theme.AntarcticaColor
import com.example.ui.theme.AsiaColor
import com.example.ui.theme.EuropeColor
import com.example.ui.theme.OceaniaColor

@Composable
fun ContinentChip(
    continent: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continentColor = getContinentColor(continent)
    val backgroundColor = if (isSelected) continentColor else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .testTag("continent_chip_${continent.lowercase()}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = continent,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun getContinentColor(continent: String): Color {
    return when (continent.lowercase()) {
        "africa" -> AfricaColor
        "americas" -> AmericasColor
        "asia" -> AsiaColor
        "europe" -> EuropeColor
        "oceania" -> OceaniaColor
        "antarctica" -> AntarcticaColor
        else -> MaterialTheme.colorScheme.primary
    }
}
