package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import com.example.R
import com.example.ui.components.readingWidth
import kotlinx.coroutines.withContext

/** The app's GitHub repository, where the catalog's research and audit files live. */
const val REPOSITORY_URL = "https://github.com/meh313/Guess-countries"

/** The full privacy statement, PRIVACY.md in the repository; the About screen's Privacy section links to it. */
const val PRIVACY_URL = "$REPOSITORY_URL/blob/main/PRIVACY.md"

/**
 * The asset with the player-facing third-party notices: one block per work (name, use, licence, copyright, licence
 * text). `tools/export-notices.py` writes it from `licenses/THIRD_PARTY_NOTICES.md`, the developers' file; CI checks it.
 */
internal const val NOTICES_ASSET = "NOTICES.txt"

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

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .readingWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("about_screen")
    ) {
        ScreenHeader(title = stringResource(R.string.about_title), onBack = onBack, backTag = "about_back_btn")

        Text(
            text = stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("about_version")
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AboutSection(stringResource(R.string.about_sources_title)) {
            Text(
                text = stringResource(R.string.about_sources_text),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { openUrl(context, REPOSITORY_URL) }, modifier = Modifier.testTag("about_repository_btn")) {
                Text(stringResource(R.string.about_open_repository))
            }
        }

        AboutSection(stringResource(R.string.about_catalog_title)) {
            Text(
                text = stringResource(R.string.about_catalog_text),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AboutSection(stringResource(R.string.about_translations_title)) {
            Text(
                text = stringResource(R.string.about_translations_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("about_translations")
            )
        }

        AboutSection(stringResource(R.string.about_privacy_title)) {
            Text(
                text = stringResource(R.string.about_privacy_text),
                style = MaterialTheme.typography.bodyMedium
            )
            // The full statement lives next to the code. Like a detail sheet's source links: a 48dp-tall row that
            // hands the address to the browser, which needs no permission of this app's.
            Text(
                text = stringResource(R.string.about_privacy_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.sheet_open_in_browser)) { openUrl(context, PRIVACY_URL) }
                    .wrapContentHeight(Alignment.CenterVertically)
                    .testTag("about_privacy_link")
            )
        }

        AboutSection(stringResource(R.string.about_licences_title)) {
            Text(
                text = stringResource(R.string.about_licences_text),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notices ?: stringResource(R.string.about_notices_loading),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("about_notices")
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
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
