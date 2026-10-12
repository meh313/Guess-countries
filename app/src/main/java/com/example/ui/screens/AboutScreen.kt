package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's GitHub repository, where the catalog's research and audit files live. */
const val REPOSITORY_URL = "https://github.com/meh313/Guess-countries"

/** The asset holding the third-party licence text (packaged from `licenses/`). */
internal const val NOTICES_ASSET = "THIRD_PARTY_NOTICES.md"

/**
 * About: what the app is, where its facts come from and how they were checked, the rules the catalog follows, the
 * licences of what it ships, and what it does with data (nothing leaves the device).
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }
    val notices by produceState<String?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("about_screen")
    ) {
        ScreenHeader(title = "About", onBack = onBack, backTag = "about_back_btn")

        Text(
            text = "World Flags $version",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("about_version")
        )
        Text(
            text = "An offline app for learning the flags, capitals and facts of the 196 countries of the world, and Antarctica.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AboutSection("Where the facts come from") {
            Text(
                text = "Each country entry was researched against public sources, mostly Wikipedia, national constitutions and official sites, " +
                    "and then checked a second time by two independent reviewers who could only change a fact they could both source. " +
                    "Population figures are the United Nations World Population Prospects 2024 estimates, rounded to three significant figures; " +
                    "the other facts were checked in 2026. The research notes, the audit and the owner's decisions are in the repository, " +
                    "and a country's detail sheet lists the pages its facts were checked against.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { openUrl(context, REPOSITORY_URL) }, modifier = Modifier.testTag("about_repository_btn")) {
                Text("Open the repository")
            }
        }

        AboutSection("What is in the catalog") {
            Text(
                text = "The 196 sovereign states as widely recognised, plus Antarctica to browse. Flags are drawn to each country's own " +
                    "proportions. Where the meaning of a flag is disputed or legendary, the text says so instead of picking a side; " +
                    "a fact that could not be settled was left out rather than guessed.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AboutSection("Privacy") {
            Text(
                text = "The app has no network permission: nothing you do leaves the device, and there is no account. Your progress is " +
                    "stored in the app's own database and included in Android's backup of the app, so it moves with you to a new phone " +
                    "when backup is on. Settings > Reset progress deletes all of it.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AboutSection("Licences") {
            Text(
                text = "The flag artwork is rendered from the flag-icons project (MIT licence). The app is built with Android Jetpack and " +
                    "Kotlin libraries under the Apache License 2.0. The full notice text:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notices ?: "Loading…",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("about_notices")
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .semantics { heading() }
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}
