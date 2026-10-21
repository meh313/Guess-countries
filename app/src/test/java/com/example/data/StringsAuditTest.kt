package com.example.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The translations against the English strings, as `tools/strings-audit.py` checks them: every key, the same
 * placeholders, every plural quantity the language needs, no Latin digit typed into the Arabic. Read from the source
 * tree like the map asset tests.
 */
class StringsAuditTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("%(\\d+\\$)?[sd]")
    private val quantities = mapOf("fr" to setOf("one", "many", "other"), "es" to setOf("one", "many", "other"), "de" to setOf("one", "other"),
        "ar" to setOf("zero", "one", "two", "few", "many", "other"))

    /** file name -> key -> (string text, or quantity -> text for a plurals). */
    private fun entries(folder: File): Map<String, Map<String, Any>> =
        folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.sortedBy { it.name }.associate { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val items = LinkedHashMap<String, Any>()
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                when (node.tagName) {
                    "string" -> items[node.getAttribute("name")] = node.textContent
                    "plurals" -> {
                        val byQuantity = LinkedHashMap<String, String>()
                        val children = node.childNodes
                        for (j in 0 until children.length) {
                            val item = children.item(j) as? Element ?: continue
                            byQuantity[item.getAttribute("quantity")] = item.textContent
                        }
                        items[node.getAttribute("name")] = byQuantity
                    }
                }
            }
            file.name to items
        }

    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    private val locales: List<String> get() = quantities.keys.sorted()

    @Test
    fun everyLocaleHasEveryFileAndKey_andNothingMore() {
        val english = entries(File(res, "values"))
        for (locale in locales) {
            val translated = entries(File(res, "values-$locale"))
            assertEquals(locale, english.keys, translated.keys)
            for ((file, items) in english) {
                assertEquals("$locale/$file", items.keys, translated.getValue(file).keys)
            }
        }
    }

    @Test
    fun placeholdersMatchTheEnglish_andPluralsHaveTheirQuantities() {
        val english = entries(File(res, "values"))
        for (locale in locales) {
            val translated = entries(File(res, "values-$locale"))
            for ((file, items) in english) {
                for ((key, value) in items) {
                    val other = translated.getValue(file).getValue(key)
                    if (value is String) {
                        assertEquals("$locale/$file $key", placeholders(value), placeholders(other as String))
                    } else {
                        @Suppress("UNCHECKED_CAST") val en = value as Map<String, String>
                        @Suppress("UNCHECKED_CAST") val tr = other as Map<String, String>
                        assertTrue("$locale/$file $key lacks ${quantities.getValue(locale) - tr.keys}", tr.keys.containsAll(quantities.getValue(locale)))
                        val allowed = placeholders(en.getValue("other"))
                        for ((quantity, text) in tr) {
                            assertTrue("$locale/$file $key/$quantity has ${placeholders(text)}", allowed.containsAll(placeholders(text)))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theEnglishIsTheReference_withNoEmptyStrings() {
        val english = entries(File(res, "values"))
        val total = english.values.sumOf { it.size }
        assertTrue("$total strings and plurals", total > 400)
        for ((file, items) in english) for ((key, value) in items) {
            if (value is String) assertTrue("$file $key is empty", value.isNotBlank())
        }
    }

    @Test
    fun arabicTypesNoLatinDigit_outsideAPlaceholder() {
        // An Arabic %d prints Arabic-Indic digits, so a "60" typed into a string would sit beside them in Latin ones.
        // The About prose (the catalog's 196, the years of the figures, licence versions) has no formatted number by it.
        val prose = setOf("about_tagline", "about_sources_text", "about_catalog_text", "about_licences_text")
        for ((file, items) in entries(File(res, "values-ar"))) for ((key, value) in items) {
            if (key in prose) continue
            @Suppress("UNCHECKED_CAST") val texts = if (value is String) listOf(value) else (value as Map<String, String>).values
            for (text in texts) assertFalse("ar/$file $key: $text", placeholder.replace(text, "").any { it in '0'..'9' })
        }
    }
}
