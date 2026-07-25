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
    val driveSide: String = "Right"
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
