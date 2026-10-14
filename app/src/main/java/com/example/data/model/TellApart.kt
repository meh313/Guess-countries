package com.example.data.model

/**
 * How to tell two look-alike flags apart, for the pairs people mix up most. A tip names the difference that settles
 * it in one sentence, written so that it reads well whichever of the two was picked by mistake.
 */
object TellApart {
    /** The tips, keyed by the two codes in alphabetical order joined by a space ("RO TD"). */
    internal val TIPS: Map<String, String> = TELL_APART_TIPS_1 + TELL_APART_TIPS_2

    /** The tip for [a] and [b] in either order, or null when the pair has none. */
    fun tip(a: String, b: String): String? = TIPS[key(a, b)]

    fun key(a: String, b: String): String = if (a < b) "$a $b" else "$b $a"
}
