package com.example

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.example.learn.LearnSession
import com.example.quiz.QuizMode
import com.example.ui.continentInSentence
import com.example.ui.continentLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The words around a number or a continent in each language, read through the resources as the screens read them. A
 * count beside a word that agrees with it comes from a plurals, so 1 is singular ("1 jour", "1 Tag") and Arabic has its
 * one, two, three-to-ten and eleven-and-up forms; the Arabic Americas take the genitive in a sentence, and German and
 * Spanish Antarctica its article; the Arabic mode arrows point the way the line reads; and a sentence put together
 * around a name reads in Spanish whatever the name.
 * StringsAuditTest checks the keys, the placeholders and the quantities; this checks what they say.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TranslationsTest {

    private val resources: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun plural(id: Int, count: Int) = resources.getQuantityString(id, count, count)

    /** [count] in the digits a `%d` of these resources prints (Arabic-Indic in Arabic). */
    private fun digits(count: Int) = String.format(resources.configuration.locales[0], "%d", count)

    @Test
    fun english_namesWhatTheMapButtonReviews() {
        assertEquals("Review 1 country on the map", plural(R.plurals.today_review_map, 1))
        assertEquals("Review 3 countries on the map", plural(R.plurals.today_review_map, 3))
        assertEquals("Start 1-question quiz", plural(R.plurals.quiz_start_quiz, 1))
        assertEquals("1-day streak, already counted today", plural(R.plurals.result_streak_already_counted, 1))
        assertEquals("Correct! +12 points", plural(R.plurals.quiz_result_correct, 12))
        assertEquals("14 correct · 340 points", resources.getString(R.string.result_blitz_score, plural(R.plurals.result_correct_count, 14), plural(R.plurals.result_points, 340)))
    }

    @Test
    fun theFixedNumbers_areOnesThePlainStringsWereWrittenFor() {
        // "Apprendre 5 nouveaux", "تعلّم ٥ جديدة": a plural from three to ten in every language, Arabic's "few".
        assertTrue(LearnSession.CARDS_PER_SESSION in 3..10)
        // "Blitz de 60 secondes", "بليتز ٦٠ ثانية": Arabic's singular noun after eleven and up.
        assertTrue(QuizMode.BLITZ.totalTimeSeconds!! >= 11)
    }

    @Test
    @Config(qualifiers = "fr")
    fun french_oneAndZeroAreSingular() {
        assertEquals("Lancer un quiz de 1 question", plural(R.plurals.quiz_start_quiz, 1))
        assertEquals("Lancer un quiz de 4 questions", plural(R.plurals.quiz_start_quiz, 4))
        assertEquals("Série de 1 jour, déjà comptée aujourd'hui", plural(R.plurals.result_streak_already_counted, 1))
        assertEquals("Série de 1 jour, déjà comptée aujourd'hui", plural(R.plurals.study_streak_already_counted, 1))
        assertEquals("1 juste", plural(R.plurals.result_correct_count, 1))
        assertEquals("0\u00a0point", plural(R.plurals.result_points, 0))
        assertEquals("1 révisé, non maîtrisé", plural(R.plurals.study_weak_detail, 1))
        // Two numbers and an adjective: the label comes first, so nothing agrees with either number.
        assertEquals("Réponses justes\u00a0: 1 / 10", resources.getString(R.string.result_correct_ratio, 1, 10))
        assertEquals("Maîtrisés\u00a0: 1 sur 54", resources.getString(R.string.stats_mastered_of, 1, 54))
    }

    @Test
    @Config(qualifiers = "es")
    fun spanish_oneIsSingular() {
        assertEquals("Iniciar quiz de 1 pregunta", plural(R.plurals.quiz_start_quiz, 1))
        assertEquals("Racha de 1 día, ya contada hoy", plural(R.plurals.result_streak_already_counted, 1))
        assertEquals("Racha de 2 días, ya contada hoy", plural(R.plurals.study_streak_already_counted, 2))
        assertEquals("1 correcta · 10 puntos", resources.getString(R.string.result_blitz_score, plural(R.plurals.result_correct_count, 1), plural(R.plurals.result_points, 10)))
        assertEquals("1 repasada, no dominada", plural(R.plurals.study_weak_detail, 1))
        assertEquals("Recordadas: 1 de 10", resources.getString(R.string.study_summary_remembered, 1, 10))
        // The timer chip has no verb to agree with the last second ("Quedan 1s").
        assertEquals("1 s", resources.getString(R.string.quiz_seconds_left_short, 1))
    }

    @Test
    @Config(qualifiers = "es")
    fun spanish_theExportLineReads_withTheFileNameOrWithout() {
        val fallback = resources.getString(R.string.settings_export_fallback_name)
        assertEquals("Exportación guardada en el archivo elegido.", resources.getString(R.string.settings_export_done, fallback))
        assertEquals("Exportación guardada en progreso.json.", resources.getString(R.string.settings_export_done, "progreso.json"))
    }

    @Test
    @Config(qualifiers = "de")
    fun german_oneIsSingular() {
        assertEquals("Quiz mit 1 Frage starten", plural(R.plurals.quiz_start_quiz, 1))
        assertEquals("Serie von 1 Tag, heute schon gezählt", plural(R.plurals.result_streak_already_counted, 1))
        assertEquals("Serie von 3 Tagen, heute schon gezählt", plural(R.plurals.study_streak_already_counted, 3))
        assertEquals("Richtig! +12 Punkte", plural(R.plurals.quiz_result_correct, 12))
    }

    @Test
    @Config(qualifiers = "de")
    fun german_antarcticaTakesItsArticleInASentence() {
        // On its own (a chip, a pill) the name alone; after "in", the dative article. The other continents take none.
        assertEquals("Antarktis", resources.continentLabel("Antarctica"))
        assertEquals("Keine Länder in der Antarktis. Filter löschen?", emptyHint("Antarctica"))
        assertEquals("Keine Länder in Amerika. Filter löschen?", emptyHint("Americas"))
        assertEquals("Keine Länder in Afrika. Filter löschen?", emptyHint("Africa"))
    }

    @Test
    @Config(qualifiers = "es")
    fun spanish_antarcticaTakesItsArticleInASentence() {
        assertEquals("Antártida", resources.continentLabel("Antarctica"))
        assertEquals("No hay países en la Antártida. ¿Quitar los filtros?", emptyHint("Antarctica"))
        assertEquals("No hay países en América. ¿Quitar los filtros?", emptyHint("Americas"))
    }

    /** Explore's empty hint with only the continent filter on, as ExploreScreen puts it together. */
    private fun emptyHint(continent: String) = resources.getString(R.string.explore_empty_hint_filtered,
        resources.getString(R.string.explore_empty_scope_continent, resources.continentInSentence(continent)))

    @Test
    @Config(qualifiers = "ar")
    fun arabic_theNounTakesTheFormOfItsNumber() {
        // One and two name the noun alone; three to ten take the plural; eleven to ninety-nine the singular accusative.
        assertEquals("ابدأ اختبارًا من سؤال واحد", plural(R.plurals.quiz_start_quiz, 1))
        assertEquals("ابدأ اختبارًا من سؤالين", plural(R.plurals.quiz_start_quiz, 2))
        assertEquals("ابدأ اختبارًا من ${digits(10)} أسئلة", plural(R.plurals.quiz_start_quiz, 10))
        assertEquals("سلسلة يومين، احتُسبت اليوم بالفعل", plural(R.plurals.result_streak_already_counted, 2))
        assertEquals("سلسلة ${digits(5)} أيام، احتُسبت اليوم بالفعل", plural(R.plurals.study_streak_already_counted, 5))
        assertEquals("سلسلة ${digits(12)} يومًا، احتُسبت اليوم بالفعل", plural(R.plurals.study_streak_already_counted, 12))
        // The first right answer of a run is worth 10 points, the next ones 12, 14…
        assertEquals("صحيح! +${digits(10)} نقاط", plural(R.plurals.quiz_result_correct, 10))
        assertEquals("صحيح! +${digits(12)} نقطة", plural(R.plurals.quiz_result_correct, 12))
        assertEquals("${digits(10)} نقاط", plural(R.plurals.result_points, 10))
        assertEquals("${digits(150)} نقطة", plural(R.plurals.stats_points, 150))
        assertEquals("إجابتان صحيحتان", plural(R.plurals.result_correct_count, 2))
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabic_numbersTypedIntoAStringAreSpelledOutOrFormatted() {
        assertEquals("بدأت السلسلة: اليوم الأول", resources.getString(R.string.streak_started))
        assertEquals("بليتز ${digits(60)} ثانية", resources.getString(R.string.mode_blitz, 60))
        assertEquals("تعلّم ${digits(5)} جديدة", resources.getString(R.string.today_learn_new, 5))
        // One and two follow what they count, as in the other plurals; the dual says "two" on its own.
        assertEquals("ادرس · مستحقة واحدة", plural(R.plurals.flashcards_study_due, 1))
        assertEquals("ادرس · مستحقتان", plural(R.plurals.flashcards_study_due, 2))
        assertEquals("مستحقة واحدة غدًا", plural(R.plurals.stats_forecast_tomorrow, 1))
        assertEquals("مستحقتان غدًا", plural(R.plurals.stats_forecast_tomorrow, 2))
        // The Today card says them the same way, on the screens it shares with the Study button and the forecast.
        assertEquals("مستحقة واحدة", plural(R.plurals.today_due, 1))
        assertEquals("مستحقتان", plural(R.plurals.today_due, 2))
        assertEquals("جديدة واحدة اليوم", plural(R.plurals.today_new, 1))
        assertEquals("جديدتان اليوم", plural(R.plurals.today_new, 2))
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabic_modeArrowsPointLeft_fromThePromptToTheAnswer() {
        val titles = listOf(R.string.mode_flag_name, R.string.mode_pick_flag, R.string.mode_capital, R.string.mode_continent,
            R.string.mode_locate, R.string.mode_type_name).map { resources.getString(it) }
        for (title in titles) {
            assertTrue(title, '←' in title)
            assertFalse(title, '→' in title)
        }
        // The flag is on the right, where the line starts, and the arrow leads from it to the country.
        assertEquals("علم ← دولة", titles.first())
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabic_theAmericasAreGenitiveInASentence() {
        // On its own (a chip, a pill) the dual is nominative; after "في" or as the second noun of an idafa, genitive.
        assertEquals("الأمريكتان", resources.getString(R.string.continent_americas))
        val inSentence = resources.getString(R.string.continent_americas_in_sentence)
        assertEquals("في الأمريكتين", resources.getString(R.string.explore_empty_scope_continent, inSentence))
        assertEquals("عرض دول الأمريكتين التي ما زالت للتعلم", resources.getString(R.string.stats_continent_open, inSentence))
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabic_theAreaUnitIsArabic() {
        assertEquals("${digits(644)} كم²", resources.getString(R.string.area_km2, digits(644)))
    }
}
