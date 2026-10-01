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
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.worldexplorer.flags"
    minSdk = 24
    targetSdk = 36
    versionCode = versionCodeFromEnv ?: 1
    versionName = "1.0"

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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // Exported Room schemas double as fixtures for migration tests.
  sourceSets {
    getByName("androidTest").assets.directories.add("$projectDir/schemas")
    // The third-party licence text ships inside the APK next to the artwork it covers.
    getByName("main").assets.directories.add("$rootDir/licenses")
  }
}

// Room writes one JSON per database version here; commit them so migrations can be tested.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // Firebase: re-add the BOM below, apply the com.google.gms.google-services plugin and add a
  // google-services.json.
  // implementation(platform(libs.firebase.bom))
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Firebase Auth with Google Sign-In requires all of the following to be uncommented together.
  // If you are using Firebase Auth with other providers (e.g. Email/Password), you may only need
  // firebase-auth.
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
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
