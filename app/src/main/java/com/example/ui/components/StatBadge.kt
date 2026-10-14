package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * One metric tile on the Progress screen. TalkBack reads it as one item, "Due today: 12" (plus [detail] when there
 * is one), instead of the value and the title one after the other. With [onClick] the tile is a button, announced
 * with [onClickLabel]; without one it is plain.
 */
@Composable
fun StatBadge(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    /** A third line under the title ("Blitz best: 14"); none by default. */
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
) {
    val description = if (detail == null) {
        stringResource(R.string.stat_badge_description, title, value)
    } else {
        stringResource(R.string.stat_badge_description_detail, title, value, detail)
    }
    val described = modifier.semantics(mergeDescendants = true) {
        contentDescription = description
        // The explicit receiver picks the semantics property, not the onClick parameter of the same name.
        if (onClick != null && onClickLabel != null) this.onClick(label = onClickLabel, action = null)
    }
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = described, shape = shape, colors = colors, elevation = elevation) {
            BadgeContent(title, value, icon, iconColor, detail)
        }
    } else {
        Card(modifier = described, shape = shape, colors = colors, elevation = elevation) {
            BadgeContent(title, value, icon, iconColor, detail)
        }
    }
}

@Composable
private fun BadgeContent(title: String, value: String, icon: ImageVector, iconColor: Color, detail: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            // "190 pts" used to wrap at large font sizes; the value shrinks a little instead of breaking.
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
