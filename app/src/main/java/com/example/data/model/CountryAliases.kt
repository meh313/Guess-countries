package com.example.data.model

/**
 * Other names people search for and type: short forms (with the article where the official name has one, "The
 * Gambia"), new and former names ("Naoero", "Burma") and the names of parts. Each finds the country whose ISO code it
 * is listed under, after the catalog's own name but before capitals, and counts as a typed answer for it. Not shown
 * anywhere. An alias belongs to one country only: "Korea" is South Korea's, so that query puts it first and North
 * Korea, found by its name, second.
 */
object CountryAliases {
    val byCode: Map<String, List<String>> = mapOf(
        "AE" to listOf("UAE", "Emirates"),
        "BA" to listOf("Bosnia"),
        "BF" to listOf("Upper Volta"),
        "BJ" to listOf("Dahomey"),
        "BN" to listOf("Brunei Darussalam"),
        "BS" to listOf("The Bahamas"),
        "CD" to listOf("DRC", "DR Congo", "Democratic Republic of Congo", "Congo-Kinshasa", "Zaire"),
        "CG" to listOf("Republic of Congo", "Congo-Brazzaville"),
        "CI" to listOf("Côte d'Ivoire"),
        "CN" to listOf("PRC"),
        "CV" to listOf("Cabo Verde"),
        "CZ" to listOf("Czechia"),
        "ET" to listOf("Abyssinia"),
        "FM" to listOf("Federated States of Micronesia"),
        "GB" to listOf("UK", "Britain", "Great Britain", "England", "Scotland", "Wales", "Northern Ireland"),
        "GH" to listOf("Gold Coast"),
        "GM" to listOf("The Gambia"),
        "IR" to listOf("Persia"),
        "KH" to listOf("Kampuchea"),
        "KP" to listOf("DPRK"),
        "KR" to listOf("Korea", "ROK"),
        "LA" to listOf("Lao"),
        "LK" to listOf("Ceylon"),
        "MK" to listOf("Macedonia"),
        "MM" to listOf("Burma"),
        "NL" to listOf("Holland"),
        "NR" to listOf("Naoero"),
        "NZ" to listOf("Aotearoa"),
        "PS" to listOf("Palestinian territories", "West Bank", "Gaza"),
        "RU" to listOf("Russian Federation"),
        "SA" to listOf("KSA"),
        "SZ" to listOf("Swaziland"),
        "TH" to listOf("Siam"),
        "TL" to listOf("East Timor"),
        "TR" to listOf("Türkiye"),
        "TW" to listOf("Formosa", "Republic of China"),
        "US" to listOf("USA", "America", "United States of America"),
        "VA" to listOf("Holy See"),
        "VN" to listOf("Viet Nam")
    )

    fun of(code: String): List<String> = byCode[code].orEmpty()
}
