package com.example

import org.junit.Assert.assertEquals
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
}
