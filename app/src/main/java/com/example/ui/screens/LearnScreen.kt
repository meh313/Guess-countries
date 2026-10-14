package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Country
import com.example.data.model.TellApart
import com.example.learn.LearnSession
import com.example.ui.components.FlagView
import com.example.ui.components.getContinentColor
import com.example.ui.components.readingWidth
import com.example.ui.viewmodel.CountryViewModel

/**
 * "Learn 5 new": five flags never seen before, one card at a time (the flag, the name, the region, how to spot it
 * and the flag it is most often confused with), then a quiz over the five on the Quiz tab. Reached from the study
 * chooser on Flashcards or the Today card; Back returns to where it was opened.
 */
@Composable
fun LearnScreen(
    viewModel: CountryViewModel,
    onBack: () -> Unit,
    /** The cards are read and the quiz has started: show the Quiz tab. */
    onQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by viewModel.learnSession.collectAsStateWithLifecycle()
    // The holder reads the skill states when a session starts; collecting them here keeps them current meanwhile.
    viewModel.skills.collectAsStateWithLifecycle()
    val countriesByCode = remember(viewModel) { viewModel.repository.allCountries.associateBy { it.code } }
    // A session already on its cards (Back and in again) goes on where it was; anything else starts afresh. Until
    // that has run the screen shows no verdict, so "every flag seen" never flashes before the first card.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (viewModel.learnSession.value?.stage != LearnSession.Stage.CARDS) viewModel.startLearn()
        started = true
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .readingWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenHeader(title = stringResource(R.string.learn_title), onBack = onBack, backTag = "learn_back_btn")

            val current = session?.takeIf { it.stage == LearnSession.Stage.CARDS }
            val country = current?.let { countriesByCode[it.current.code] }
            when {
                current != null && country != null -> {
                    val lookAlike = current.current.lookAlike?.let { countriesByCode[it] }
                    Text(
                        text = stringResource(R.string.learn_card_position, current.index + 1, current.cards.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("learn_progress")
                    )
                    Text(
                        text = stringResource(R.string.learn_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LearnCardView(
                        country = country,
                        lookAlike = lookAlike,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.previousLearnCard() },
                            enabled = !current.isFirst,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("learn_prev_btn")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.learn_previous))
                        }
                        Button(
                            onClick = {
                                if (current.isLast) {
                                    viewModel.startLearnQuiz()
                                    if (viewModel.learnSession.value?.stage == LearnSession.Stage.QUIZ) onQuiz()
                                } else {
                                    viewModel.nextLearnCard()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("learn_next_btn")
                        ) {
                            Text(if (current.isLast) stringResource(R.string.learn_quiz_me) else stringResource(R.string.learn_next))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                started -> Text(
                    text = stringResource(R.string.learn_nothing_new),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag("learn_empty")
                )
            }
        }
    }
}

/**
 * One Learn card, in the shape of a flashcard's back: the region, the flag, the name, the cue to spot the flag and,
 * when there is one, the flag most often confused with it (and how to tell the two apart, where a tip exists).
 */
@Composable
private fun LearnCardView(country: Country, lookAlike: Country?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.testTag("learn_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = getContinentColor(country.continent)) {
                Text(
                    text = country.regionLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FlagView(
                country = country,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .widthIn(max = 280.dp)
                    .testTag("learn_flag")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = country.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("learn_name")
            )
            if (country.spotIt.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.spot_it_label),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = country.spotIt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("learn_spot_it")
                        )
                    }
                }
            }
            if (lookAlike != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("learn_look_alike")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.learn_not_to_confuse),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagView(country = lookAlike, modifier = Modifier.width(56.dp), thumbnail = true)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = lookAlike.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TellApart.tip(country.code, lookAlike.code)?.let { tip ->
                                    Text(
                                        text = tip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.testTag("learn_tell_apart")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
