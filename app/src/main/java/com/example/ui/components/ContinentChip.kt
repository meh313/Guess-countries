package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.continentLabel
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
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }

    // The pill itself is 40dp tall; the 4dp above and below it belong to the touch target, which is 48dp. The
    // ripple is drawn inside the pill, not over the whole target.
    Row(
        modifier = modifier
            .testTag("continent_chip_${continent.lowercase()}")
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, interactionSource = interactionSource, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp)
            .shadow(elevation = if (isSelected) 4.dp else 0.dp, shape = shape)
            .background(backgroundColor, shape)
            .clip(shape)
            .indication(interactionSource, ripple())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = continentLabel(continent),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
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
