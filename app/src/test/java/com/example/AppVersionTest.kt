package com.example

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The version the build gives the app. The code follows versionName as major*10000 + minor*100 + patch, the way the
 * Release workflow numbers every release, so a build made outside the workflow does not get a code (it once defaulted
 * to 1) that the Play Console or an installed release refuses. VERSION_CODE, when set, overrides it.
 */
class AppVersionTest {

    @Test
    fun theVersionCodeIsDerivedFromTheVersionName() {
        val (major, minor, patch) = (BuildConfig.VERSION_NAME.split('.').map { it.toInt() } + 0).take(3)
        val override = System.getenv("VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() }?.toInt()
        assertEquals(override ?: (major * 10_000 + minor * 100 + patch), BuildConfig.VERSION_CODE)
    }

    /**
     * A version is released with its notes: the Release workflow appends release-notes/<version>.md to the GitHub
     * release, and the store listing's "What's new" block is the text for the Play Console. A bump of versionName
     * without either fails here, not on release day.
     */
    @Test
    fun theReleaseNotesAndTheStoreListingNameThisVersion() {
        val version = BuildConfig.VERSION_NAME
        val notes = File("../release-notes/$version.md")
        assertTrue("${notes.path} is missing", notes.exists())
        assertTrue("${notes.path} does not start with its heading", notes.readText().startsWith("**What's new in $version**"))
        val listing = File("../store/listing.md").readText()
        assertTrue("store/listing.md has no What's new block for $version", "**What's new ($version)**" in listing)
    }
}
