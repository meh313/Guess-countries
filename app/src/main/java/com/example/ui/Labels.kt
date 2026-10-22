package com.example.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.Country
import com.example.data.model.SortOption
import com.example.data.settings.ThemeMode
import com.example.data.settings.TimerPace
import com.example.flashcards.StudySession
import com.example.progress.Skill
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.ui.components.Standing

/*
 * The words for the values the pure code works with. Quiz modes, scopes, continents, sort orders and standings are
 * stored and compared as enum names or English keys; what the player reads comes from the string resources, so a
 * translation touches `res/values-xx/` and nothing else. Nothing here is stored.
 */

/** The resource of a quiz mode's title, which [title] reads: the blitz's takes its length. */
private val QuizMode.titleRes: Int
    @StringRes get() = when (this) {
        QuizMode.FLAG_NAME -> R.string.mode_flag_name
        QuizMode.PICK_FLAG -> R.string.mode_pick_flag
        QuizMode.CAPITAL -> R.string.mode_capital
        QuizMode.CONTINENT -> R.string.mode_continent
        QuizMode.LOCATE -> R.string.mode_locate
        QuizMode.TYPE_NAME -> R.string.mode_type_name
        QuizMode.SPEED_MATCH -> R.string.mode_speed_round
        QuizMode.BLITZ -> R.string.mode_blitz
    }

/**
 * The title of a quiz mode on the setup screen, the score screen and the quiz log. The blitz's names its length
 * ("60-second blitz") through a placeholder, so the number has the digits of the other numbers on screen.
 */
@Composable
fun QuizMode.title(): String = totalTimeSeconds?.let { stringResource(titleRes, it) } ?: stringResource(titleRes)

/** The extra line under a mode's title on the setup screen; null where the title says it all. */
val QuizMode.hintRes: Int?
    @StringRes get() = when (this) {
        QuizMode.PICK_FLAG -> R.string.mode_pick_flag_hint
        QuizMode.CONTINENT -> R.string.mode_continent_hint
        QuizMode.LOCATE -> R.string.mode_locate_hint
        QuizMode.TYPE_NAME -> R.string.mode_type_name_hint
        QuizMode.SPEED_MATCH -> R.string.mode_speed_round_hint
        QuizMode.BLITZ -> R.string.mode_blitz_hint
        QuizMode.FLAG_NAME, QuizMode.CAPITAL -> null
    }

/** What a question of this mode about [target] asks; the same wording as [QuizEngine.questionText], from resources. */
@Composable
fun QuizMode.questionText(target: Country): String = when (this) {
    QuizMode.FLAG_NAME -> stringResource(R.string.question_flag_name)
    QuizMode.PICK_FLAG -> stringResource(R.string.question_pick_flag, target.name)
    QuizMode.CAPITAL -> stringResource(R.string.question_capital, target.name)
    QuizMode.CONTINENT -> stringResource(R.string.question_continent)
    QuizMode.LOCATE -> stringResource(R.string.question_locate)
    QuizMode.TYPE_NAME -> stringResource(R.string.question_type_name)
    QuizMode.BLITZ -> stringResource(R.string.question_blitz)
    QuizMode.SPEED_MATCH -> stringResource(R.string.question_speed_round)
}

/** The title of a quiz log entry's mode: a quiz mode by its stored name, a study session, or the raw value. */
@Composable
fun modeTitle(storedMode: String): String = when (storedMode) {
    StudySession.MODE -> stringResource(R.string.mode_flashcards)
    else -> QuizMode.entries.firstOrNull { it.name == storedMode }?.title() ?: storedMode
}

val QuizDifficulty.titleRes: Int
    @StringRes get() = when (this) {
        QuizDifficulty.NORMAL -> R.string.difficulty_normal
        QuizDifficulty.HARD -> R.string.difficulty_hard
    }

val QuizDifficulty.hintRes: Int
    @StringRes get() = when (this) {
        QuizDifficulty.NORMAL -> R.string.difficulty_normal_hint
        QuizDifficulty.HARD -> R.string.difficulty_hard_hint
    }

val ThemeMode.titleRes: Int
    @StringRes get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

val TimerPace.titleRes: Int
    @StringRes get() = when (this) {
        TimerPace.NORMAL -> R.string.timer_normal
        TimerPace.RELAXED -> R.string.timer_relaxed
        TimerPace.OFF -> R.string.timer_off
    }

/** "Sort by Name": the sort menu's entries. */
val SortOption.labelRes: Int
    @StringRes get() = when (this) {
        SortOption.NAME -> R.string.sort_by_name
        SortOption.POPULATION -> R.string.sort_by_population
        SortOption.AREA -> R.string.sort_by_area
        SortOption.CONTINENT -> R.string.sort_by_continent
    }

/** "name", "population": the sort order inside a sentence ("Sorted by population"). */
val SortOption.nounRes: Int
    @StringRes get() = when (this) {
        SortOption.NAME -> R.string.sort_noun_name
        SortOption.POPULATION -> R.string.sort_noun_population
        SortOption.AREA -> R.string.sort_noun_area
        SortOption.CONTINENT -> R.string.sort_noun_continent
    }

/** "not reviewed yet", "still learning", "mastered": a standing inside a sentence. */
val Standing.labelRes: Int
    @StringRes get() = when (this) {
        Standing.UNSEEN -> R.string.standing_unseen
        Standing.WEAK -> R.string.standing_weak
        Standing.MASTERED -> R.string.standing_mastered
    }

/** "Unseen", "Learning", "Mastered": a standing on a chip. */
val Standing.chipLabelRes: Int
    @StringRes get() = when (this) {
        Standing.UNSEEN -> R.string.standing_chip_unseen
        Standing.WEAK -> R.string.standing_chip_weak
        Standing.MASTERED -> R.string.standing_chip_mastered
    }

/** "unseen", "learning", "mastered": a standing after a skill name ("Flag: learning"). */
val Standing.shortLabelRes: Int
    @StringRes get() = when (this) {
        Standing.UNSEEN -> R.string.standing_short_unseen
        Standing.WEAK -> R.string.standing_short_weak
        Standing.MASTERED -> R.string.standing_short_mastered
    }

/**
 * The standing filter as a label: "Still to learn" for the pair a Progress continent card sets, otherwise the chip
 * labels joined ("Unseen or Mastered"); null when nothing is filtered.
 */
@Composable
fun standingFilterLabel(standings: Set<Standing>): String? = when {
    standings.isEmpty() -> null
    standings == Standing.STILL_TO_LEARN -> stringResource(R.string.standing_still_to_learn)
    else -> Standing.entries.filter { it in standings }.map { stringResource(it.chipLabelRes) }.reduce { a, b -> stringResource(R.string.standing_either, a, b) }
}

/** [standingFilterLabel] inside a sentence: "still to learn", "mastered", "not reviewed yet or still learning". */
@Composable
fun standingFilterPhrase(standings: Set<Standing>): String? = when {
    standings.isEmpty() -> null
    standings == Standing.STILL_TO_LEARN -> stringResource(R.string.standing_phrase_still_to_learn)
    else -> Standing.entries.filter { it in standings }.map { stringResource(it.labelRes) }.reduce { a, b -> stringResource(R.string.standing_either, a, b) }
}

val Skill.labelRes: Int
    @StringRes get() = when (this) {
        Skill.FLAG -> R.string.skill_flag
        Skill.CAPITAL -> R.string.skill_capital
        Skill.CONTINENT -> R.string.skill_continent
        Skill.LOCATION -> R.string.skill_location
    }

/** The resource for a continent name of the catalog (or "All"), null for a name that is not one. */
@StringRes
fun continentLabelRes(name: String): Int? = when (name.lowercase()) {
    "all" -> R.string.continent_all
    "africa" -> R.string.continent_africa
    "americas" -> R.string.continent_americas
    "asia" -> R.string.continent_asia
    "europe" -> R.string.continent_europe
    "oceania" -> R.string.continent_oceania
    "antarctica" -> R.string.continent_antarctica
    else -> null
}

/** The resource for a quiz scope or study source key (a continent, Global, Weak spots, Due today, Mistakes, Learn, This deck, Capitals due). */
@StringRes
fun scopeLabelRes(scope: String): Int? = when (scope) {
    QuizEngine.GLOBAL -> R.string.scope_global
    QuizEngine.WEAK_SPOTS -> R.string.scope_weak_spots
    QuizEngine.DUE -> R.string.scope_due_today
    QuizEngine.MISTAKES -> R.string.scope_mistakes
    QuizEngine.LEARN -> R.string.scope_learn
    StudySession.DECK -> R.string.study_source_deck
    StudySession.CAPITALS_DUE -> R.string.study_source_capitals_due
    else -> continentLabelRes(scope)
}

/** A continent name as the player reads it; an unknown name is shown as it is. */
@Composable
fun continentLabel(name: String): String = continentLabelRes(name)?.let { stringResource(it) } ?: name

/**
 * The resource for a continent name inside a sentence, after a preposition or a noun ("No countries in the Americas",
 * "the countries still to learn in the Americas"). Two continents change: English gives the Americas "the", French
 * says them in the singular after "en" ("en Amérique") and Arabic puts their dual in the genitive (الأمريكتين); German
 * and Spanish give Antarctica its article ("in der Antarktis", "en la Antártida"). Any other name is
 * [continentLabelRes]'s.
 */
@StringRes
fun continentInSentenceRes(name: String): Int? = when (name.lowercase()) {
    "americas" -> R.string.continent_americas_in_sentence
    "antarctica" -> R.string.continent_antarctica_in_sentence
    else -> continentLabelRes(name)
}

/** [continentLabel] inside a sentence (see [continentInSentenceRes]); an unknown name is shown as it is. */
@Composable
fun continentInSentence(name: String): String = continentInSentenceRes(name)?.let { stringResource(it) } ?: name

/**
 * "Afrique · Southern Africa": the country's continent as the player reads it, then its subregion, which stays English
 * like the rest of the catalog; the continent alone where the subregion repeats it (Antarctica). [inSentence] names the
 * continent as [continentInSentence] does, for a label inside a sentence (the map's "in the Americas · South America").
 */
@Composable
fun regionLabel(country: Country, inSentence: Boolean = false): String =
    country.regionLabel(if (inSentence) continentInSentence(country.continent) else continentLabel(country.continent))

/** A scope or study source key as the player reads it; an unknown key is shown as it is. */
@Composable
fun scopeLabel(scope: String): String = scopeLabelRes(scope)?.let { stringResource(it) } ?: scope

/** [scopeLabel] outside composition (a snackbar text, a description built in a callback). */
fun Resources.scopeLabel(scope: String): String = scopeLabelRes(scope)?.let { getString(it) } ?: scope

/** [continentLabel] outside composition. */
fun Resources.continentLabel(name: String): String = continentLabelRes(name)?.let { getString(it) } ?: name

/**
 * An option of a [mode] question as the player reads it: a continent in the app's language (the option itself stays
 * the catalog's English key, which is what is scored and logged), anything else as it is.
 */
@Composable
fun optionLabel(mode: QuizMode, option: String): String = if (mode == QuizMode.CONTINENT) continentLabel(option) else option

/** [continentInSentence] outside composition (the sentence read aloud about a country). */
fun Resources.continentInSentence(name: String): String = continentInSentenceRes(name)?.let { getString(it) } ?: name
