package com.example.data.model

/**
 * Antarctica: browsable like a country, but not quizzable (see Country.isSovereign).
 * Entries are alphabetical by name; tools/gen-entries.py inserts new ones in place.
 */
object AntarcticaCatalog {
    val countries: List<Country> = listOf(
        Country(
            code = "AQ",
            name = "Antarctica",
            officialName = "Antarctic Treaty area",
            capital = "None (largest base: McMurdo Station)",
            continent = "Antarctica",
            subregion = "Antarctica",
            population = 1100L, // Winter population; about 5,000 in summer
            areaSqKm = 14200000.0,
            flagEmoji = "🇦🇶",
            flagColors = listOf("Blue", "White"),
            flagDescription = "Antarctica has no official flag, because no single government rules it. Graham Bartram made this popular unofficial design in the 1990s. The white map shows the continent itself. The blue background and plain look are meant to show neutrality, and the design was inspired by the United Nations flag and the emblem of the Antarctic Treaty.",
            languages = listOf("None (Antarctic Treaty texts: English, French, Russian, Spanish)"),
            currency = "None (stations use their home countries' currencies)",
            landmarks = listOf("South Pole Station", "Blood Falls", "Mount Erebus"),
            funFact = "Antarctica holds about 90% of the world's ice and is the coldest, windiest and driest continent.",
            driveSide = "N/A",
            quizCapital = "None",
            isSovereign = false
        )
    )
}
