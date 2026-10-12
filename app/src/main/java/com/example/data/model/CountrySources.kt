package com.example.data.model

import android.content.Context
import org.json.JSONObject

/**
 * The web pages each country's facts were checked against, from the asset `sources.json` that
 * `tools/export-sources.py` writes out of `content/research` and `content/audit`. Read once, on first use, off the
 * main thread by the caller. Countries of the first catalog have no research records and are not in the file.
 */
class CountrySources private constructor(private val byCode: Map<String, List<String>>) {
    /** The URLs for [code], in the order they were first cited; empty when none are on record. */
    fun of(code: String): List<String> = byCode[code].orEmpty()

    val codes: Set<String> get() = byCode.keys

    companion object {
        const val ASSET = "sources.json"

        fun load(context: Context): CountrySources =
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }

        fun parse(json: String): CountrySources {
            val root = JSONObject(json)
            val map = HashMap<String, List<String>>()
            for (code in root.keys()) {
                val urls = root.getJSONArray(code)
                map[code] = List(urls.length()) { urls.getString(it) }
            }
            return CountrySources(map)
        }
    }
}
