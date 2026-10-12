package com.example

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.SettingsScreen
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

    companion object {
        /** Reached from Progress, not from the bar; Back returns there. */
        const val SETTINGS = "settings"
        const val ABOUT = "about"
    }
}

class MainActivity : ComponentActivity() {
    /** The tab a launching intent asks for (the reminder notification opens the quiz); cleared once shown. */
    private val requestedTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh start follows the intent: after a rotation the player may have moved to another tab.
        if (savedInstanceState == null) requestedTab.value = intent.requestedTab()
        setContent {
            // The theme follows the settings, so the view model is created here, above the theme.
            val viewModel: CountryViewModel = viewModel(factory = CountryViewModel.Factory)
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            WorldFlagsTheme(
                darkTheme = appearance.themeMode.isDark(isSystemInDarkTheme()),
                dynamicColor = appearance.dynamicColor
            ) {
                MainAppScreen(viewModel = viewModel, requestedTab = requestedTab.value, onTabRequestShown = { requestedTab.value = null })
            }
        }
    }

    /**
     * The activity is already running (SINGLE_TOP) and a notification was tapped: switch tabs, keep everything else.
     * The intent is not stored with setIntent: nothing reads it after onCreate, and keeping one with NEW_TASK on a
     * running activity stops Robolectric's ActivityScenario from ever seeing the activity destroyed.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.requestedTab()?.let { requestedTab.value = it }
    }

    private fun Intent.requestedTab(): String? = getStringExtra(EXTRA_OPEN_TAB)

    companion object {
        /** String extra naming the tab to open: one of the [Screen] routes. */
        const val EXTRA_OPEN_TAB = "com.example.OPEN_TAB"
        const val TAB_QUIZ = "quiz"
    }
}

@Composable
fun MainAppScreen(
    viewModel: CountryViewModel = viewModel(factory = CountryViewModel.Factory),
    requestedTab: String? = null,
    onTabRequestShown: () -> Unit = {}
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

    // Don't keep talking after the user moves to another tab. The route is null while the back stack
    // is restored after a rotation, so only a change between two known routes counts.
    var lastRoute by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            if (lastRoute != null && lastRoute != currentRoute) viewModel.stopSpeaking()
            lastRoute = currentRoute
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                items.forEach { screen ->
                    val isSelected = currentRoute == screen.route ||
                        (screen == Screen.Stats && (currentRoute == Screen.SETTINGS || currentRoute == Screen.ABOUT))
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = null // the label below already names the tab
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
                    StatsScreen(viewModel = viewModel, onOpenSettings = { navController.navigate(Screen.SETTINGS) { launchSingleTop = true } })
                }
                composable(Screen.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onAbout = { navController.navigate(Screen.ABOUT) { launchSingleTop = true } }
                    )
                }
                composable(Screen.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
            // After the NavHost, whose own effects give the controller its graph: effects run in composition order.
            LaunchedEffect(requestedTab) {
                val route = items.firstOrNull { it.route == requestedTab }?.route ?: return@LaunchedEffect
                if (currentRoute != route) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                onTabRequestShown()
            }
        }
    }
}
