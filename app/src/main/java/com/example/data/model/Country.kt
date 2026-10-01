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
    val flagType: FlagStyle,
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
    val isSovereign: Boolean = true
)

enum class FlagStyle {
    VERTICAL_STRIPES_3,
    HORIZONTAL_STRIPES_3,
    HORIZONTAL_STRIPES_2,
    CANTON_STARS,
    CENTER_CIRCLE,
    CROSS_NORDIC,
    COMPLEX_EMBLEM
}
