package com.example.data.model

data class Country(
    val code: String,
    val name: String,
    val officialName: String,
    val capital: String,
    val continent: String,
    val subregion: String,
    val population: Long,
    val areaSqKm: Double,
    val flagEmoji: String,
    val flagColors: List<String>,
    val flagDescription: String,
    val languages: List<String>,
    val currency: String,
    val landmarks: List<String>,
    val funFact: String,
    val driveSide: String = "Right",
    /**
     * One city, for places that have room for only one: the capital quiz and the country cards. It differs
     * from [capital] where several cities share the role.
     */
    val quizCapital: String = capital,
    /**
     * False for Antarctica: a continent with no government, capital, currency or official flag. It stays
     * browsable but is left out of quizzes and mastery totals.
     */
    val isSovereign: Boolean = true,
    /**
     * How to recognise the flag at a glance, in one short sentence ("Three vertical bands, blue at the pole, then
     * white and red."). Shown on the flashcard back, in the detail sheet and after a miss. Empty until written.
     */
    val spotIt: String = ""
) {
    /**
     * "Africa · Southern Africa", or just "Antarctica" where the subregion repeats the continent. The screens name the
     * continent in their language ([continentName], see `ui/Labels.kt`); the subregion is the catalog's English.
     */
    fun regionLabel(continentName: String = continent): String =
        if (subregion.equals(continent, ignoreCase = true)) continentName else "$continentName · $subregion"

    /** The key the country lists sort names by: lowercase, accents dropped. Folded once, since sorting compares it often. */
    val nameSortKey: String by lazy { fold(name) }
}
