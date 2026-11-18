import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Notifications need the instance's Firebase project (`google-services.json`, never committed): without it
// the app builds and simply cannot receive them.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

/**
 * Release builds are signed with the owner's own key, the same as the React Native app's, so passkeys
 * and Google sign-in trust both and the cutover (OS-1191) installs over it. Never committed.
 */
val signing =
    (System.getenv("ZANKUP_SIGNING") ?: "${System.getProperty("user.home")}/.config/zankup/android/signing.properties")
        .let(::File)
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.zankup.android"
    compileSdk = 37

    defaultConfig {
        // Beside the React Native app until the cutover, when it becomes com.zankup.app.
        applicationId = "com.zankup.next"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Its own link scheme beside the React Native app's `zankup://` until the cutover.
        manifestPlaceholders["linkScheme"] = "zankup-next"
        buildConfigField("String", "LINK_SCHEME", "\"zankup-next\"")
    }

    signingConfigs {
        if (signing != null) {
            create("owner") {
                storeFile = File(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["linkScheme"] = "zankup-next-dev"
            buildConfigField("String", "LINK_SCHEME", "\"zankup-next-dev\"")
            // The owner's key here too, so passkeys and Google sign-in trust Zankup Next Dev as well.
            signingConfigs.findByName("owner")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("owner") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        error += "HardcodedText"
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:session"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(project(":feature:signin"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
