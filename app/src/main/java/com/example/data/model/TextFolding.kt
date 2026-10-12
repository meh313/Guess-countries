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

private val SAINT = Regex("\\bsaint\\b")
private val NOT_A_WORD = Regex("[^a-z0-9]+")

/**
 * [fold] for searching: words only, separated by single spaces, and "Saint" written as "St", so "St Lucia",
 * "St. Lucia" and "Saint Lucia" are the same query and "cote d" still finds Côte d'Ivoire.
 */
fun searchKey(text: String): String = NOT_A_WORD.replace(SAINT.replace(fold(text), "st"), " ").trim()

/** True when [needle] starts a word of this search key ("uk" in "ukraine", not in "yamoussoukro"). */
fun String.hasWordStarting(needle: String): Boolean = startsWith(needle) || contains(" $needle")
