package com.example.data.model

/**
 * The classic capital traps: for a country whose largest or best-known city is not its capital, that city. A
 * capital question about the country offers one of them among the wrong answers, in place of a random capital.
 * None of these is the capital of any country in the catalog, and a country whose trap is itself named in its own
 * capital text, as a capital of any kind or the seat of government, is left out: Bolivia (La Paz), Benin (Cotonou),
 * Burundi (Bujumbura), Ivory Coast (Abidjan) and Sri Lanka (Colombo). Hand-written; keep it to cities nobody
 * disputes.
 */
object DecoyCities {
    val byCode: Map<String, List<String>> = mapOf(
        "AE" to listOf("Dubai"),
        "AU" to listOf("Sydney", "Melbourne"),
        "BR" to listOf("São Paulo", "Rio de Janeiro"),
        "BZ" to listOf("Belize City"),
        "CA" to listOf("Toronto", "Montreal"),
        "CH" to listOf("Zurich", "Geneva"),
        "CM" to listOf("Douala"),
        "CN" to listOf("Shanghai"),
        "EC" to listOf("Guayaquil"),
        "IN" to listOf("Mumbai"),
        "KZ" to listOf("Almaty"),
        "MA" to listOf("Casablanca"),
        "MM" to listOf("Yangon"),
        "NG" to listOf("Lagos"),
        "NZ" to listOf("Auckland"),
        "PH" to listOf("Quezon City"),
        "PK" to listOf("Karachi", "Lahore"),
        "PW" to listOf("Koror"),
        "TR" to listOf("Istanbul"),
        "TZ" to listOf("Dar es Salaam"),
        "US" to listOf("New York"),
        "VN" to listOf("Ho Chi Minh City"),
        "ZA" to listOf("Johannesburg")
    )

    /** The decoys of [code], none for a country without a famous non-capital. */
    fun of(code: String): List<String> = byCode[code].orEmpty()
}
