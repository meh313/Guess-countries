package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.components.FlagView
import com.example.ui.theme.StreakColor
import com.example.ui.viewmodel.CountryViewModel

private val QuizMode.icon: ImageVector
    get() = when (this) {
        QuizMode.FLAG_NAME -> Icons.Default.Flag
        QuizMode.CAPITAL -> Icons.Default.LocationCity
        QuizMode.CONTINENT -> Icons.Default.Public
        QuizMode.SPEED_MATCH -> Icons.Default.Speed
    }

@Composable
fun QuizScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier
) {
    val allCountries = viewModel.repository.allCountries
    // The running quiz lives in the ViewModel; the setup choices survive rotation via rememberSaveable.
    val session by viewModel.quizSession.collectAsState()
    var selectedMode by rememberSaveable { mutableStateOf(QuizMode.FLAG_NAME) }
    var selectedContinentScope by rememberSaveable { mutableStateOf("Global") }
    val quizScrollState = rememberScrollState()
    var showQuitConfirm by rememberSaveable { mutableStateOf(false) }

    val active = session

    if (active == null) {
        // QUIZ CONFIGURATION SCREEN
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Geography & Flag Quiz",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Test your knowledge & climb the leaderboard!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Select Game Mode
            Text(
                text = "Select Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuizMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectedMode = mode }
                            )
                            .testTag("quiz_mode_${mode.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Select Continent Scope
            Text(
                text = "Select Scope",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QuizEngine.SCOPES) { scope ->
                    val isSelected = selectedContinentScope == scope
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { selectedContinentScope = scope }
                            )
                            .testTag("quiz_scope_${scope.lowercase()}")
                    ) {
                        Text(
                            text = scope,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start Quiz Button
            val scopePoolSize = QuizEngine.poolFor(selectedContinentScope, allCountries).size
            val canStart = scopePoolSize >= QuizEngine.MIN_POOL
            Button(
                onClick = { viewModel.startQuiz(selectedMode, selectedContinentScope) },
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_quiz_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (canStart) {
                        "Start ${QuizEngine.questionCount(scopePoolSize)}-Question Quiz"
                    } else {
                        "Needs at least ${QuizEngine.MIN_POOL} countries"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        // ACTIVE QUIZ SCREEN
        val q = active.current

        // The quiz now outlives the screen, so offer a way out (Back asks first).
        BackHandler(enabled = !active.isFinished) { showQuitConfirm = true }
        val questionCount = active.questions.size

        // Each new question starts at the top so the flag is visible.
        LaunchedEffect(active.currentIndex) { quizScrollState.scrollTo(0) }
        // After answering, bring the explanation and Next button into view on small screens.
        LaunchedEffect(active.hasAnswered) {
            if (active.hasAnswered) {
                withFrameNanos { } // wait one frame so the explanation card has been measured
                quizScrollState.animateScrollTo(quizScrollState.maxValue)
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
                .verticalScroll(quizScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status Bar: Score & Streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Question ${active.currentIndex + 1} of $questionCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Score: ${active.score}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = StreakColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Streak ${active.streak}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { showQuitConfirm = true },
                        modifier = Modifier.testTag("quiz_quit_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Quit quiz")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (active.currentIndex + 1).toFloat() / questionCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Question Visual Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (active.mode.showsFlag) {
                        FlagView(
                            country = q.targetCountry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = q.questionText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4 Options Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { index, optionText ->
                    val isSelected = active.selectedAnswerIndex == index
                    val isCorrect = index == q.correctAnswerIndex

                    val containerColor = when {
                        !active.hasAnswered -> MaterialTheme.colorScheme.surface
                        isCorrect -> Color(0xFF2E7D32) // Green
                        isSelected && !isCorrect -> Color(0xFFC62828) // Red
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val textColor = if (active.hasAnswered && (isCorrect || isSelected)) Color.White else MaterialTheme.colorScheme.onSurface

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !active.hasAnswered) { viewModel.answerQuiz(index) }
                            .testTag("quiz_option_$index"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )

                            if (active.hasAnswered && isCorrect) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            } else if (active.hasAnswered && isSelected && !isCorrect) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Post-Answer Explanation & Next Button
            if (active.hasAnswered) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💡 ${q.targetCountry.name} (${q.targetCountry.capital})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = q.targetCountry.funFact,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.nextQuizQuestion() },
                            modifier = Modifier.fillMaxWidth().testTag("next_question_btn")
                        ) {
                            Text(if (active.isLastQuestion) "Finish & View Score" else "Next Question")
                        }
                    }
                }
            }
        }
    }

    if (showQuitConfirm && active != null && !active.isFinished) {
        AlertDialog(
            onDismissRequest = { showQuitConfirm = false },
            title = { Text("Quit this quiz?") },
            text = { Text("Your progress in this quiz will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitConfirm = false
                        viewModel.endQuiz()
                    },
                    modifier = Modifier.testTag("quiz_quit_confirm_btn")
                ) {
                    Text("Quit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showQuitConfirm = false },
                    modifier = Modifier.testTag("quiz_quit_cancel_btn")
                ) {
                    Text("Keep playing")
                }
            }
        )
    }

    // Finish Celebration Dialog
    if (active?.isFinished == true) {
        AlertDialog(
            onDismissRequest = { viewModel.endQuiz() },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quiz Complete!", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Your Score: ${active.score} Points",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Great job expanding your geographical knowledge!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.endQuiz() },
                    modifier = Modifier.testTag("quiz_finish_done_btn")
                ) {
                    Text("Done")
                }
            }
        )
    }
}
