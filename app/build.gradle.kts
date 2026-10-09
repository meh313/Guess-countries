plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

// Release signing is optional. It is applied only when the keystore file and both passwords are
// available (KEYSTORE_PATH or ./my-upload-key.jks, STORE_PASSWORD, KEY_PASSWORD). Without them
// assembleRelease still works and produces an unsigned APK, so fresh clones and CI can build it.
val releaseKeystore = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
val releaseStorePassword: String? = System.getenv("STORE_PASSWORD")
val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD")
val canSignRelease =
  releaseKeystore.exists() && releaseStorePassword != null && releaseKeyPassword != null
if (!canSignRelease && (releaseStorePassword != null || releaseKeyPassword != null)) {
  logger.warn("Release signing skipped: set KEYSTORE_PATH (or add my-upload-key.jks) together with STORE_PASSWORD and KEY_PASSWORD.")
}

// CI can pass VERSION_CODE (for example the build number) so every upload is unique. A bad value
// fails the build instead of silently falling back to 1.
val versionCodeFromEnv: Int? =
  System.getenv("VERSION_CODE")?.takeIf { it.isNotBlank() }?.let { raw ->
    raw.trim().toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
      ?: error("VERSION_CODE must be an integer in 1..2100000000, got '$raw'")
  }

android {
  namespace = "com.example"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.aistudio.worldexplorer.flags"
    minSdk = 24
    targetSdk = 36
    versionCode = versionCodeFromEnv ?: 1
    versionName = "1.9"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (canSignRelease) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = releaseStorePassword
        keyAlias = "upload"
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (canSignRelease) signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // debug.keystore is gitignored, so only use it when present. Fresh clones fall back to
      // AGP's auto-generated debug key instead of failing at the signing step.
      if (file("${rootDir}/debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Robolectric 4.17 needs these module openings on JDK 17+ (robolectric.org/getting-started).
      all {
        it.jvmArgs(
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
          "--add-opens=java.base/java.io=ALL-UNNAMED",
          "--add-opens=java.base/java.net=ALL-UNNAMED",
          "--add-opens=java.base/java.security=ALL-UNNAMED",
          "--add-opens=java.base/java.text=ALL-UNNAMED",
          "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
          "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
        )
      }
    }
  }
  // Exported Room schemas double as fixtures for migration tests.
  sourceSets {
    getByName("androidTest").assets.directories.add("$projectDir/schemas")
    // The third-party licence text ships inside the APK next to the artwork it covers.
    getByName("main").assets.directories.add("$rootDir/licenses")
  }
}

// Room writes one JSON per database version here; commit them so migrations can be tested.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
