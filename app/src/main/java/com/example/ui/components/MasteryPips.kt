package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import com.example.ui.labelRes
import com.example.ui.shortLabelRes
import com.example.ui.theme.MasteredColor
import com.example.ui.theme.NeedsPracticeColor

/** How a country stands in one skill, for the small indicators and the detail sheet. */
enum class Standing {
    UNSEEN,
    WEAK,
    MASTERED;

    companion object {
        fun of(state: SkillState?, now: Long): Standing = when {
            state == null || state.isNew -> UNSEEN
            SkillMastery.isMastered(state, now) -> MASTERED
            else -> WEAK
        }

        /** The standings a Progress continent card opens Explore on: everything that is not mastered yet. */
        val STILL_TO_LEARN: Set<Standing> = setOf(UNSEEN, WEAK)
    }
}

/** The size of one pip; the legend on Explore draws them at the same size. */
val PipSize: Dp = 10.dp

/**
 * Two pips on a country card: the flag skill, then the capital skill. Each standing has a shape as well as a colour
 * (an outlined circle until reviewed, a half-filled one while learning, a filled one with a tick once mastered), so
 * the row reads without colour. One description for TalkBack: "Flag: mastered, Capital: learning".
 */
@Composable
fun MasteryPips(flag: Standing, capital: Standing, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.pips_description,
        stringResource(Skill.FLAG.labelRes),
        stringResource(flag.shortLabelRes),
        stringResource(Skill.CAPITAL.labelRes),
        stringResource(capital.shortLabelRes)
    )
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("progress_pips"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pip(flag)
        Pip(capital)
    }
}

/** One pip in the shape and colour of [standing]; decorative on its own (the row or the legend says what it means). */
@Composable
fun Pip(standing: Standing, modifier: Modifier = Modifier) {
    val color = when (standing) {
        Standing.UNSEEN -> MaterialTheme.colorScheme.outline
        Standing.WEAK -> NeedsPracticeColor
        Standing.MASTERED -> MasteredColor
    }
    Box(modifier = modifier.size(PipSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(PipSize)) {
            val stroke = 1.5.dp.toPx()
            when (standing) {
                Standing.UNSEEN -> drawCircle(color, radius = (size.minDimension - stroke) / 2, style = Stroke(width = stroke))
                Standing.WEAK -> {
                    drawCircle(color, radius = (size.minDimension - stroke) / 2, style = Stroke(width = stroke))
                    // The left half, filled: a pip half way there.
                    drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
                }
                Standing.MASTERED -> drawCircle(color)
            }
        }
        if (standing == Standing.MASTERED) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(PipSize - 2.dp)
            )
        }
    }
}

/** The standing of [code] in [skill] from the per-skill states the screens hold. */
fun Map<Skill, Map<String, SkillState>>.standing(code: String, skill: Skill, now: Long): Standing =
    Standing.of(this[skill]?.get(code), now)

/** Grey for the unseen pip; a named constant so the detail sheet can match it. */
val UnseenColor: Color = Color(0xFFB0B8C4)
