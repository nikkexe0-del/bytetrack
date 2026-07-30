plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// THE PROBLEM THIS SOLVES: "release" builds were never signed with anything, so CI's
// assembleRelease output couldn't be installed at all on a normal device, let alone install IN
// PLACE over a previous version - Android refuses an update whose APK is signed with a different
// key than what's already installed (INSTALL_FAILED_UPDATE_INCOMPATIBLE), which is exactly the
// "why do users have to reinstall every time" problem. The fix is a real, STABLE keystore used
// for every release build, every time, so every release you ever publish can update the last one.
//
// Locally, these env vars won't be set, so the release build type falls back to debug signing
// (see buildTypes below) - fine for testing on your own device, but NEVER hand that build to
// anyone else, since it's signed with a different (Android Studio auto-generated, non-stable)
// key than whatever CI produces - installing it would permanently fork that user off the update
// chain until they uninstall and start over.
//
// One-time setup:
//   1. keytool -genkey -v -keystore bytetrack-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias bytetrack
//   2. base64 -i bytetrack-release.jks | tr -d '\n' > keystore-base64.txt   (macOS: base64 -i ..., Linux: base64 -w0 ...)
//   3. In the GitHub repo: Settings > Secrets and variables > Actions, add:
//        BYTETRACK_KEYSTORE_BASE64   - contents of keystore-base64.txt
//        BYTETRACK_KEYSTORE_PASSWORD - the keystore password from step 1
//        BYTETRACK_KEY_ALIAS         - "bytetrack" (or whatever alias you used)
//        BYTETRACK_KEY_PASSWORD      - the key password from step 1
//   4. Keep bytetrack-release.jks itself OUT of git - back it up somewhere safe instead. Losing
//      it means every future release permanently breaks the update chain for existing installs.
val releaseKeystorePath: String? = System.getenv("BYTETRACK_RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("BYTETRACK_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("BYTETRACK_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("BYTETRACK_KEY_PASSWORD")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.zestyy.bytetrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zestyy.bytetrack"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to debug signing locally when the secrets above aren't set - see the
            // big comment at the top of this file for why that's fine for local testing only.
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // needed for BuildConfig.VERSION_NAME, used by the update checker
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager for periodic sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Charts (simple canvas-based, no external dep needed - see ui/components/UsageChart.kt)

    debugImplementation("androidx.compose.ui:ui-tooling")
}
