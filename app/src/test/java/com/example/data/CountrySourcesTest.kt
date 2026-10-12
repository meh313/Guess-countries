package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CountrySources
import com.example.quiz.allCountries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The per-country sources asset that `tools/export-sources.py` writes from the research and audit files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountrySourcesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun everyListedCountryIsInTheCatalog_andHasWebAddressesOnly() {
        val sources = CountrySources.load(context)
        val codes = allCountries.map { it.code }.toSet()

        assertTrue(sources.codes.isNotEmpty())
        sources.codes.forEach { code ->
            assertTrue("$code is not in the catalog", code in codes)
            val urls = sources.of(code)
            assertTrue("$code has no sources", urls.isNotEmpty())
            assertTrue("$code: $urls", urls.all { it.startsWith("https://") || it.startsWith("http://") })
            assertEquals("$code lists a page twice", urls.distinct(), urls)
        }
    }

    @Test
    fun everyResearchedCountryIsCovered_theFirstCatalogIsNot() {
        val sources = CountrySources.load(context)

        // The 164 researched countries, plus the first-catalog countries the audit touched.
        assertTrue("${sources.codes.size} countries", sources.codes.size >= 164)
        assertTrue("AL" in sources.codes && "ZW" in sources.codes)
        assertTrue("a first-catalog country never researched", sources.of("AQ").isEmpty())
    }

    @Test
    fun parse_readsTheFormat() {
        val parsed = CountrySources.parse("""{"FR":["https://a","https://b"],"DE":[]}""")

        assertEquals(listOf("https://a", "https://b"), parsed.of("FR"))
        assertTrue(parsed.of("DE").isEmpty())
        assertTrue(parsed.of("ZZ").isEmpty())
    }
}
