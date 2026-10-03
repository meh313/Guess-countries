package com.example.data.model

/** The built-in country dataset, kept apart from the repository so editing data never touches logic. */
object CountryCatalog {
    /** The year the population figures estimate; shown next to them, since they are rounded estimates. */
    const val DATA_YEAR = 2024

    /** Every entry: the five UN M49 regions, each alphabetical by name, then Antarctica. */
    val all: List<Country> =
        AfricaCatalog.countries + AmericasCatalog.countries + AsiaCatalog.countries +
            EuropeCatalog.countries + OceaniaCatalog.countries + AntarcticaCatalog.countries
}
