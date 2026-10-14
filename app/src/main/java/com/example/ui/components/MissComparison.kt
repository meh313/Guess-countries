package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Country

/**
 * The teaching moment after a wrong text answer: the flag of the country that was picked beside the flag of the
 * right one, each under its label, so the two are seen side by side once. One description for TalkBack:
 * "You picked Austria, the answer is Latvia". [pickedCaption] and [correctCaption] name what each flag stands for
 * (the country, or "Vienna, Austria" in the capital quiz).
 */
@Composable
fun MissComparison(
    picked: Country,
    correct: Country,
    modifier: Modifier = Modifier,
    pickedCaption: String = picked.name,
    correctCaption: String = correct.name
) {
    val description = stringResource(R.string.miss_description, pickedCaption, correctCaption)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("quiz_miss_comparison"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Side(label = stringResource(R.string.miss_you_picked), country = picked, caption = pickedCaption, tint = Color(0xFFC62828), modifier = Modifier.weight(1f))
        Side(label = stringResource(R.string.miss_correct), country = correct, caption = correctCaption, tint = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
    }
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
