package com.example.data.model

/**
 * Other names people search for: short forms, former names and the names of parts. Each finds the country whose
 * ISO code it is listed under, after the catalog's own name but before capitals. Not shown anywhere.
 */
object CountryAliases {
    val byCode: Map<String, List<String>> = mapOf(
        "AE" to listOf("UAE", "Emirates"),
        "BA" to listOf("Bosnia"),
        "BN" to listOf("Brunei Darussalam"),
        "CD" to listOf("DRC", "DR Congo", "Congo-Kinshasa", "Zaire"),
        "CG" to listOf("Congo-Brazzaville"),
        "CI" to listOf("Côte d'Ivoire"),
        "CN" to listOf("PRC"),
        "CV" to listOf("Cabo Verde"),
        "CZ" to listOf("Czechia"),
        "FM" to listOf("Federated States of Micronesia"),
        "GB" to listOf("UK", "Britain", "Great Britain", "England", "Scotland", "Wales", "Northern Ireland"),
        "IR" to listOf("Persia"),
        "KH" to listOf("Kampuchea"),
        "KP" to listOf("DPRK", "Korea"),
        "KR" to listOf("Korea", "ROK"),
        "LA" to listOf("Lao"),
        "LK" to listOf("Ceylon"),
        "MK" to listOf("Macedonia"),
        "MM" to listOf("Burma"),
        "NL" to listOf("Holland"),
        "PS" to listOf("Palestinian territories", "West Bank", "Gaza"),
        "RU" to listOf("Russian Federation"),
        "SZ" to listOf("Swaziland"),
        "TL" to listOf("East Timor"),
        "TR" to listOf("Türkiye"),
        "TW" to listOf("Formosa", "Republic of China"),
        "US" to listOf("USA", "America", "United States of America"),
        "VA" to listOf("Holy See"),
        "VN" to listOf("Viet Nam")
    )

    fun of(code: String): List<String> = byCode[code].orEmpty()
}
