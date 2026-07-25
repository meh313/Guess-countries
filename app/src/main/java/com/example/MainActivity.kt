package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.FlashcardScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Explore : Screen("explore", "Explore", Icons.Default.Public)
    object Flashcards : Screen("flashcards", "Flashcards", Icons.Default.Style)
    object Quiz : Screen("quiz", "Quiz", Icons.Default.EmojiEvents)
    object Stats : Screen("stats", "Progress", Icons.Default.BarChart)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorldFlagsTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: CountryViewModel = viewModel()
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Explore,
        Screen.Flashcards,
        Screen.Quiz,
        Screen.Stats
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("nav_tab_${screen.route}")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Explore.route
            ) {
                composable(Screen.Explore.route) {
                    ExploreScreen(viewModel = viewModel)
                }
                composable(Screen.Flashcards.route) {
                    FlashcardScreen(viewModel = viewModel)
                }
                composable(Screen.Quiz.route) {
                    QuizScreen(viewModel = viewModel)
                }
                composable(Screen.Stats.route) {
                    StatsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
