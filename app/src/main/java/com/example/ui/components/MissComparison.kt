package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Country
import com.example.data.model.TellApart

/**
 * The teaching moment after a wrong text answer: the flag of the country that was picked beside the flag of the
 * right one, each under its label, so the two are seen side by side once. Under them, one line for next time: the
 * [tip] that tells the two flags apart, or failing that how to spot the right one. One description for TalkBack:
 * "You picked Austria, the answer is Latvia. Latvia's red is a deep maroon…". [pickedCaption] and [correctCaption]
 * name what each flag stands for (the country, or "Vienna, Austria" in the capital quiz). With [showMap] a tiny map
 * of the right country sits under its flag (the quiz modes with time to look at it); it adds nothing to the
 * description, which already names the country.
 */
@Composable
fun MissComparison(
    picked: Country,
    correct: Country,
    modifier: Modifier = Modifier,
    pickedCaption: String = picked.name,
    correctCaption: String = correct.name,
    tip: String? = TellApart.tip(picked.code, correct.code),
    showMap: Boolean = false
) {
    val outcome = stringResource(R.string.miss_description, pickedCaption, correctCaption)
    val cue = tellApartLine(correct, tip)
    val description = if (cue == null) outcome else stringResource(R.string.miss_description_with_cue, outcome, cue)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("quiz_miss_comparison")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Side(label = stringResource(R.string.miss_you_picked), country = picked, caption = pickedCaption, tint = Color(0xFFC62828), modifier = Modifier.weight(1f))
            Side(label = stringResource(R.string.miss_correct), country = correct, caption = correctCaption, tint = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
        }
        if (showMap) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    WorldMap(country = correct, modifier = Modifier.width(72.dp), aspectRatio = 4f / 3f, testTag = "miss_map")
                }
            }
        }
        if (cue != null) {
            TellApartLine(text = cue, modifier = Modifier.padding(top = 8.dp).testTag("miss_tell_apart"))
        }
    }
}

/**
 * The one line that helps next time: [tip], the sentence that tells the two flags apart, or without one the
 * [correct] flag's spot-it cue under its "Spot it" label ("Spot it: Three vertical bands…"). Null when there is
 * neither.
 */
@Composable
fun tellApartLine(correct: Country, tip: String?): String? =
    tip ?: correct.spotIt.takeIf { it.isNotBlank() }?.let { stringResource(R.string.spot_it_line, stringResource(R.string.spot_it_label), it) }

/** How [tellApartLine] is shown: a small quiet line under the flags or the result. */
@Composable
fun TellApartLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun Side(label: String, country: Country, caption: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        FlagView(country = country, modifier = Modifier.fillMaxWidth(), contentDescription = null, thumbnail = true)
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
