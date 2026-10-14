package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SortOption
import com.example.ui.CountrySummary
import com.example.ui.chipLabelRes
import com.example.ui.components.ContinentChip
import com.example.ui.components.CountryCard
import com.example.ui.components.Pip
import com.example.progress.Skill
import com.example.ui.components.Standing
import com.example.ui.components.TodayCard
import com.example.ui.components.standing
import com.example.ui.continentLabel
import com.example.ui.labelRes
import com.example.ui.standingFilterPhrase
import com.example.ui.viewmodel.CountryViewModel

@Composable
fun ExploreScreen(
    viewModel: CountryViewModel,
    modifier: Modifier = Modifier,
    /** Opens the Flashcards tab, where the study session the Today card starts is shown. */
    onOpenFlashcards: () -> Unit = {},
    /** Opens the Learn screen (five new countries), from the Today card. */
    onLearn: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val todayCardVisible by viewModel.todayCardVisible.collectAsStateWithLifecycle()
    val selectedContinent by viewModel.selectedContinent.collectAsStateWithLifecycle()
    val showOnlyBookmarks by viewModel.showOnlyBookmarks.collectAsStateWithLifecycle()
    val countries by viewModel.filteredCountries.collectAsStateWithLifecycle()
    val userProgressMap by viewModel.userProgressMap.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val standingFilter by viewModel.standingFilter.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    // The clock is read once per sheet opening and per change of the skills (a finished quiz), not on every
    // recomposition: the standings only move when the skills do, and the sheet gets a parameter that holds still.
    val now = remember(selectedCountry?.code, skills) { viewModel.repository.now() }
    val speechAvailable by viewModel.speechAvailable.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()

    val continents = listOf("All", "Africa", "Americas", "Asia", "Europe", "Oceania", "Antarctica")
    var showSortMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val resources = LocalResources.current
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    // One scrolling grid: the banner and the controls are full-width items. That keeps countries
    // visible in landscape and on tablets, where a fixed header used to fill the whole screen,
    // and lets the number of columns follow the available width.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("explore_grid")
    ) {
        // Hero Header Card with Banner Image
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_world_banner),
                        contentDescription = null, // decorative
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.explore_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = stringResource(R.string.explore_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Today: the goal and the one next thing to do; left out once put away for the day.
        if (todayCardVisible) {
            item(key = "today", span = { GridItemSpan(maxLineSpan) }) {
                TodayCard(viewModel = viewModel, onOpenFlashcards = onOpenFlashcards, onLearn = onLearn)
            }
        }

        // Search Field & Filter Controls
        item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text(stringResource(R.string.explore_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null // the field's placeholder already says what it is
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.explore_clear_search))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_country_input"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    // The keyboard's action key reads Search and puts the keyboard away; the list already filtered.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Continent Filter Chips Row
                LazyRow(
                    modifier = Modifier.testTag("continent_chips"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(continents) { continent ->
                        ContinentChip(
                            continent = continent,
                            isSelected = selectedContinent == continent,
                            onClick = { viewModel.onContinentSelect(continent) }
                        )
                    }
                }

                // Standing chips: how the player stands on the flag. Each chip toggles its own standing in the filter;
                // a Progress continent card opens Explore with Unseen and Learning set together. Each chip carries
                // the pip the cards draw for that standing, so the row is also the legend.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("standing_chips"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Standing.entries.forEach { standing ->
                        FilterChip(
                            selected = standing in standingFilter,
                            onClick = { viewModel.toggleStanding(standing) },
                            label = { Text(stringResource(standing.chipLabelRes), style = MaterialTheme.typography.labelMedium) },
                            // The chip doubles as the legend: the same pip the cards draw for this standing.
                            leadingIcon = { Pip(standing, modifier = Modifier.testTag("standing_pip_${standing.name.lowercase()}")) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("standing_chip_${standing.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Sort & Bookmark Filter Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.countries_count, countries.size, countries.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Bookmarked Only Toggle Chip
                        FilterChip(
                            selected = showOnlyBookmarks,
                            onClick = { viewModel.toggleBookmarksOnlyFilter() },
                            label = { Text(stringResource(R.string.explore_saved_chip), style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showOnlyBookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.testTag("bookmarks_filter_chip")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sort Button & Dropdown Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.explore_sort_options),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                // The current order is ticked, so the menu says what the list is doing.
                                SortOption.entries.forEach { option ->
                                    val current = sortBy == option
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes)) },
                                        trailingIcon = { if (current) Icon(imageVector = Icons.Default.Check, contentDescription = null) },
                                        onClick = {
                                            viewModel.onSortSelect(option)
                                            showSortMenu = false
                                        },
                                        modifier = Modifier
                                            .semantics { selected = current }
                                            .testTag("sort_option_${option.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (countries.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                val noSavedYet = showOnlyBookmarks && searchQuery.isBlank() && selectedContinent == "All" && standingFilter.isEmpty()
                val anyFilter = showOnlyBookmarks || searchQuery.isNotBlank() || selectedContinent != "All" || standingFilter.isNotEmpty()
                // The hint names what is filtered: "No countries still to learn in Europe. Clear the filters?"
                val filters = listOfNotNull(
                    standingFilterPhrase(standingFilter),
                    if (selectedContinent != "All") stringResource(R.string.explore_empty_scope_continent, continentLabel(selectedContinent)) else null,
                    if (searchQuery.isNotBlank()) stringResource(R.string.explore_empty_scope_search, searchQuery.trim()) else null,
                    if (showOnlyBookmarks) stringResource(R.string.explore_empty_scope_saved) else null
                ).joinToString(" ")
                val hint = when {
                    noSavedYet -> stringResource(R.string.explore_empty_saved_hint)
                    filters.isNotEmpty() -> stringResource(R.string.explore_empty_hint_filtered, filters)
                    else -> stringResource(R.string.explore_empty_hint)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (noSavedYet) Icons.Default.BookmarkBorder else Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(if (noSavedYet) R.string.explore_empty_saved_title else R.string.explore_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("explore_empty_hint")
                        )
                        if (anyFilter) {
                            TextButton(
                                onClick = { viewModel.clearFilters() },
                                modifier = Modifier.testTag("explore_clear_filters_btn")
                            ) {
                                Text(stringResource(R.string.action_clear_filters))
                            }
                        }
                    }
                }
            }
        } else {
            items(countries, key = { it.code }) { country ->
                CountryCard(
                    country = country,
                    progress = userProgressMap[country.code],
                    onCardClick = { viewModel.selectCountry(country) },
                    onFavoriteClick = { viewModel.toggleFavorite(country.code) },
                    flagStanding = if (country.isSovereign) skills.standing(country.code, Skill.FLAG, now) else null,
                    capitalStanding = if (country.isSovereign) skills.standing(country.code, Skill.CAPITAL, now) else null
                )
            }
        }
    }

    // Modal Bottom Sheet when country selected
    selectedCountry?.let { country ->
        // Bind the speech engine only once the user can actually ask for it.
        LaunchedEffect(Unit) { viewModel.prepareSpeech() }
        val sources by viewModel.sources.collectAsStateWithLifecycle()
        CountryDetailSheet(
            country = country,
            progress = userProgressMap[country.code],
            sources = sources?.of(country.code).orEmpty(),
            skillStates = if (country.isSovereign) Skill.entries.associateWith { skills[it]?.get(country.code) } else emptyMap(),
            now = now,
            onDismiss = { viewModel.selectCountry(null) },
            onSpeak = { viewModel.speakCountryDetails(country, CountrySummary.spoken(resources, country)) },
            speechAvailable = speechAvailable,
            speechState = speechState,
            onInstallVoiceData = { viewModel.installVoiceData() },
            onToggleFavorite = { viewModel.toggleFavorite(country.code) }
        )
    }
}
