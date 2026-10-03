package com.example.data

import com.example.quiz.allCountries
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Editorial decisions by the app's owner about which places are in the catalog. They are recorded here so
 * that a future "complete the list" pass cannot undo them silently; change the sets only with the owner.
 */
class CatalogPolicyTest {

    /** Scope: UN members and observers plus Kosovo and Taiwan, minus the owner's exclusions. */
    private val excludedByOwner = setOf("IL")

    /** Partially recognised places the owner chose not to include (no entry, no artwork). */
    private val notIncluded = setOf("EH")

    @Test
    fun theOwnersExclusionsHaveNoEntry() {
        val codes = allCountries.map { it.code }.toSet()
        (excludedByOwner + notIncluded).forEach { assertTrue("$it must not be in the catalog", it !in codes) }
    }
}
