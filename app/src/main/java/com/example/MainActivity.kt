package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AboutScreen
import com.example.ui.components.Standing
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.FlashcardScreen
import com.example.ui.screens.LearnScreen
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    object Explore : Screen("explore", R.string.nav_explore, Icons.Default.Public)
    object Flashcards : Screen("flashcards", R.string.nav_flashcards, Icons.Default.Style)
    object Quiz : Screen("quiz", R.string.nav_quiz, Icons.Default.EmojiEvents)
    object Stats : Screen("stats", R.string.nav_progress, Icons.Default.BarChart)

    companion object {
        /** Reached from Progress, not from the bar; Back returns there. */
        const val SETTINGS = "settings"
        const val ABOUT = "about"

        /** "Learn 5 new", opened over whichever tab asked for it (the Flashcards study chooser, the Today card); Back returns there. */
        const val LEARN = "learn"
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

    fun isSelected(screen: Screen) = currentRoute == screen.route ||
        (screen == Screen.Stats && (currentRoute == Screen.SETTINGS || currentRoute == Screen.ABOUT)) ||
        // Learn sits over whichever tab opened it: that tab stays selected.
        (currentRoute == Screen.LEARN && navController.previousBackStackEntry?.destination?.route == screen.route)
    fun open(screen: Screen) {
        when {
            currentRoute == screen.route -> Unit
            // The tab is selected but a screen sits over its root (Settings or About over Progress): back to the root.
            isSelected(screen) -> navController.popBackStack(screen.route, inclusive = false)
            else -> navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    /** Opens "Learn 5 new" over the current tab, like Settings over Progress; Back returns to the tab. */
    fun openLearn() {
        if (currentRoute != Screen.LEARN) navController.navigate(Screen.LEARN) { launchSingleTop = true }
    }
    // A bar along the bottom on phones; on a window 600dp or wider (tablets, landscape phones with room) a rail
    // along the start edge, which leaves the height to the content.
    val windowWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    // At 1.5 times the font size or more, four labels no longer fit side by side: only the selected tab keeps its label.
    val showAllLabels = LocalDensity.current.fontScale < 1.5f
    val useRail = windowWidthDp >= RAIL_MIN_WIDTH_DP.dp

    // A database write that failed (see WriteErrors): told once, then cleared so the next failure is told again.
    val snackbarHostState = remember { SnackbarHostState() }
    val writeError by viewModel.writeError.collectAsStateWithLifecycle()
    val writeErrorText = stringResource(R.string.write_error)
    LaunchedEffect(writeError) {
        if (writeError) {
            snackbarHostState.showSnackbar(writeErrorText, withDismissAction = true, duration = SnackbarDuration.Long)
            viewModel.clearWriteError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.testTag("write_error_snackbar")) },
        bottomBar = {
            if (!useRail) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = null) }, // the label names the tab
                            label = { TabLabel(stringResource(screen.titleRes)) },
                            alwaysShowLabel = showAllLabels,
                            selected = isSelected(screen),
                            onClick = { open(screen) },
                            modifier = Modifier.testTag("nav_tab_${screen.route}")
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding)) {
            if (useRail) {
                NavigationRail(modifier = Modifier.testTag("navigation_rail")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    items.forEach { screen ->
                        NavigationRailItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                            label = { TabLabel(stringResource(screen.titleRes)) },
                            alwaysShowLabel = showAllLabels,
                            selected = isSelected(screen),
                            onClick = { open(screen) },
                            modifier = Modifier.testTag("nav_tab_${screen.route}")
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Explore.route
            ) {
                composable(Screen.Explore.route) {
                    ExploreScreen(viewModel = viewModel, onOpenFlashcards = { open(Screen.Flashcards) }, onLearn = { openLearn() })
                }
                composable(Screen.Flashcards.route) {
                    FlashcardScreen(viewModel = viewModel, onLearn = { openLearn() })
                }
                composable(Screen.Quiz.route) {
                    QuizScreen(viewModel = viewModel)
                }
                composable(Screen.Stats.route) {
                    StatsScreen(
                        viewModel = viewModel,
                        onOpenSettings = { navController.navigate(Screen.SETTINGS) { launchSingleTop = true } },
                        onOpenExplore = { continent ->
                            viewModel.showInExplore(continent, Standing.STILL_TO_LEARN)
                            open(Screen.Explore)
                        },
                        onOpenFlashcards = { open(Screen.Flashcards) },
                        onLearn = { openLearn() }
                    )
                }
                composable(Screen.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onAbout = { navController.navigate(Screen.ABOUT) { launchSingleTop = true } },
                        speechState = viewModel.speechState,
                        onInstallVoiceData = viewModel::installVoiceData
                    )
                }
                composable(Screen.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.LEARN) {
                    LearnScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onQuiz = {
                            // The cards are done: leave Learn, so the tab under it comes back plain, and play the quiz.
                            navController.popBackStack()
                            open(Screen.Quiz)
                        }
                    )
                }
            }
            // After the NavHost, whose own effects give the controller its graph: effects run in composition order.
            LaunchedEffect(requestedTab) {
                val screen = items.firstOrNull { it.route == requestedTab } ?: return@LaunchedEffect
                open(screen)
                onTabRequestShown()
            }
        }
    }
}

/** A tab label on one line: at large font sizes it shrinks a little instead of breaking "Flashcards" in two. */
@Composable
private fun TabLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp)
    )
}

/** Windows at least this wide get the navigation rail instead of the bottom bar. */
const val RAIL_MIN_WIDTH_DP = 600
