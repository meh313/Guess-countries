package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.progress.Skill
import com.example.progress.SkillMastery
import com.example.progress.SkillState
import com.example.ui.labelRes
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
    }
}

/**
 * Two pips on a country card: the flag skill, then the capital skill, grey until reviewed, amber while learning,
 * green once mastered. One description for TalkBack: "Flag mastered, capital still learning".
 */
@Composable
fun MasteryPips(flag: Standing, capital: Standing, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pips_description, stringResource(flag.labelRes), stringResource(capital.labelRes))
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("progress_pips"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Pip(flag)
        Pip(capital)
    }
}

@Composable
private fun Pip(standing: Standing) {
    val color = when (standing) {
        Standing.UNSEEN -> MaterialTheme.colorScheme.outlineVariant
        Standing.WEAK -> NeedsPracticeColor
        Standing.MASTERED -> MasteredColor
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

/** The standing of [code] in [skill] from the per-skill states the screens hold. */
fun Map<Skill, Map<String, SkillState>>.standing(code: String, skill: Skill, now: Long): Standing =
    Standing.of(this[skill]?.get(code), now)

/** Grey for the unseen pip; a named constant so the detail sheet can match it. */
val UnseenColor: Color = Color(0xFFB0B8C4)
