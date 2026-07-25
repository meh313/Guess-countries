package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Country
import com.example.ui.components.FlagView
import com.example.ui.viewmodel.CountryViewModel

enum class QuizMode(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FLAG_NAME("Flag -> Country", Icons.Default.Flag),
    CAPITAL("Country -> Capital", Icons.Default.LocationCity),
    CONTINENT("Flag -> Continent", Icons.Default.Public),
    SPEED_MATCH("Speed Round", Icons.Default.Speed)
}

data class QuizQuestion(
    val targetCountry: Country,
    val questionText: String,
    val options: List<String>,
    val correctAnswerIndex: Int
)

@Composable
fun QuizScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier
) {
    val countries by viewModel.filteredCountries.collectAsState()
    var selectedMode by remember { mutableStateOf(QuizMode.FLAG_NAME) }
    var selectedContinentScope by remember { mutableStateOf("Global") }

    var isQuizActive by remember { mutableStateOf(false) }
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var questions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }

    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var showExplanation by remember { mutableStateOf(false) }
    var isQuizFinished by remember { mutableStateOf(false) }

    fun startNewQuiz() {
        val pool = if (selectedContinentScope == "Global") {
            countries
        } else {
            countries.filter { it.continent.equals(selectedContinentScope, ignoreCase = true) }
        }

        if (pool.size < 4) return

        val generatedQuestions = pool.shuffled().take(10).map { target ->
            when (selectedMode) {
                QuizMode.FLAG_NAME -> {
                    val wrongOptions = (pool - target).shuffled().take(3).map { it.name }
                    val options = (wrongOptions + target.name).shuffled()
                    QuizQuestion(
                        targetCountry = target,
                        questionText = "Which country does this flag belong to?",
                        options = options,
                        correctAnswerIndex = options.indexOf(target.name)
                    )
                }
                QuizMode.CAPITAL -> {
                    val wrongOptions = (pool - target).shuffled().take(3).map { it.capital }
                    val options = (wrongOptions + target.capital).shuffled()
                    QuizQuestion(
                        targetCountry = target,
                        questionText = "What is the capital city of ${target.name}?",
                        options = options,
                        correctAnswerIndex = options.indexOf(target.capital)
                    )
                }
                QuizMode.CONTINENT -> {
                    val allContinents = listOf("Africa", "Americas", "Asia", "Europe", "Oceania")
                    val wrongContinents = (allContinents - target.continent).shuffled().take(3)
                    val options = (wrongContinents + target.continent).shuffled()
                    QuizQuestion(
                        targetCountry = target,
                        questionText = "Which continent is ${target.name} located in?",
                        options = options,
                        correctAnswerIndex = options.indexOf(target.continent)
                    )
                }
                QuizMode.SPEED_MATCH -> {
                    val wrongOptions = (pool - target).shuffled().take(3).map { it.name }
                    val options = (wrongOptions + target.name).shuffled()
                    QuizQuestion(
                        targetCountry = target,
                        questionText = "Identify the country for this flag:",
                        options = options,
                        correctAnswerIndex = options.indexOf(target.name)
                    )
                }
            }
        }

        questions = generatedQuestions
        currentQuestionIndex = 0
        score = 0
        streak = 0
        selectedAnswerIndex = null
        showExplanation = false
        isQuizFinished = false
        isQuizActive = true
    }

    if (!isQuizActive) {
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
                QuizMode.values().forEach { mode ->
                    val isSelected = selectedMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
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

            val scopes = listOf("Global", "Africa", "Americas", "Asia", "Europe", "Oceania")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scopes) { scope ->
                    val isSelected = selectedContinentScope == scope
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { selectedContinentScope = scope }
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
            Button(
                onClick = { startNewQuiz() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_quiz_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Start 10-Question Quiz", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // ACTIVE QUIZ SCREEN
        if (questions.isEmpty()) return
        val q = questions[currentQuestionIndex]

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
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
                        text = "Question ${currentQuestionIndex + 1} of 10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Score: $score",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFE67E22)
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
                            text = "Streak $streak",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (currentQuestionIndex + 1) / 10f },
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
                    if (selectedMode == QuizMode.FLAG_NAME || selectedMode == QuizMode.CONTINENT || selectedMode == QuizMode.SPEED_MATCH) {
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
                    val isSelected = selectedAnswerIndex == index
                    val isCorrect = index == q.correctAnswerIndex

                    val containerColor = when {
                        selectedAnswerIndex == null -> MaterialTheme.colorScheme.surface
                        isCorrect -> Color(0xFF2E7D32) // Green
                        isSelected && !isCorrect -> Color(0xFFC62828) // Red
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val textColor = if (selectedAnswerIndex != null && (isCorrect || isSelected)) Color.White else MaterialTheme.colorScheme.onSurface

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = selectedAnswerIndex == null) {
                                selectedAnswerIndex = index
                                val correct = index == q.correctAnswerIndex
                                if (correct) {
                                    score += 10 + (streak * 2)
                                    streak += 1
                                    viewModel.updateMastery(q.targetCountry.code, true)
                                } else {
                                    streak = 0
                                    viewModel.updateMastery(q.targetCountry.code, false)
                                }
                                showExplanation = true
                            }
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

                            if (selectedAnswerIndex != null && isCorrect) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                            } else if (selectedAnswerIndex != null && isSelected && !isCorrect) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Post-Answer Explanation & Next Button
            if (showExplanation) {
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
                            onClick = {
                                if (currentQuestionIndex < 9) {
                                    currentQuestionIndex++
                                    selectedAnswerIndex = null
                                    showExplanation = false
                                } else {
                                    isQuizFinished = true
                                    viewModel.saveQuizResult(selectedMode.name, score, 100, selectedContinentScope)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("next_question_btn")
                        ) {
                            Text(if (currentQuestionIndex < 9) "Next Question" else "Finish & View Score")
                        }
                    }
                }
            }
        }
    }

    // Finish Celebration Dialog
    if (isQuizFinished) {
        AlertDialog(
            onDismissRequest = { isQuizActive = false },
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
                        text = "Your Score: $score Points",
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
                    onClick = {
                        isQuizActive = false
                        isQuizFinished = false
                    },
                    modifier = Modifier.testTag("quiz_finish_done_btn")
                ) {
                    Text("Done")
                }
            }
        )
    }
}
