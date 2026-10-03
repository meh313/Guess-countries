package com.example.data.model

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Letters that do not decompose into a base letter plus an accent. */
private val SPECIAL_LETTERS = mapOf('ø' to "o", 'æ' to "ae", 'ß' to "ss", 'ł' to "l", 'đ' to "d", 'ð' to "d", 'þ' to "th")

/**
 * Lowercase text without accents, so a search for "Brasilia" finds Brasília and "Côte d'Ivoire" sorts
 * under C. Used for both searching and ordering, so the two always agree.
 */
fun fold(text: String): String =
    COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        .replace('’', '\'')
        .lowercase()
        .map { SPECIAL_LETTERS[it] ?: it.toString() }
        .joinToString("")

/** The key the country lists sort names by. */
val Country.nameSortKey: String get() = fold(name)
