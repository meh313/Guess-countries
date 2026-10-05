package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.data.model.Country

/**
 * The answers of a "pick the flag" question: up to four flags in two rows of two.
 *
 * Each card keeps the tag `quiz_option_<index>`, the green and red result colours and the "Correct answer" and
 * "Your answer, incorrect" descriptions of the text options, so the same tests and the same screen readers work
 * for both. Before an answer a card is only "Option 2 of 4" (the flag is the question, its name would give it
 * away); after the answer every card says whose flag it is.
 */
@Composable
fun FlagOptionGrid(
    optionCodes: List<String>,
    countriesByCode: Map<String, Country>,
    correctIndex: Int,
    selectedIndex: Int?,
    answered: Boolean,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        optionCodes.chunked(2).forEachIndexed { row, codes ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                codes.forEachIndexed { column, code ->
                    val index = row * 2 + column
                    val country = countriesByCode[code]
                    val isCorrect = index == correctIndex
                    val isSelected = index == selectedIndex
                    val containerColor = when {
                        !answered -> MaterialTheme.colorScheme.surface
                        isCorrect -> Color(0xFF2E7D32)
                        isSelected -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !answered) { onPick(index) }
                            .semantics {
                                if (answered && isCorrect) {
                                    stateDescription = "Correct answer"
                                } else if (answered && isSelected) {
                                    stateDescription = "Your answer, incorrect"
                                }
                            }
                            .testTag("quiz_option_$index"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.padding(6.dp)) {
                            if (country != null) {
                                FlagView(
                                    country = country,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentDescription =
                                        if (answered) "Option ${index + 1}, the flag of ${country.name}"
                                        else "Option ${index + 1} of ${optionCodes.size}"
                                )
                            }
                            if (answered && isCorrect) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                )
                            } else if (answered && isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                )
                            }
                        }
                    }
                }
                // An odd last row keeps its card at half width instead of stretching.
                if (codes.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
