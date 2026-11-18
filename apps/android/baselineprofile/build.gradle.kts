// The Baseline Profile and the Macrobenchmarks (OS-1180), run on a phone with the app signed in:
//   ./gradlew :app:generateReleaseBaselineProfile      (writes app/src/release/generated/baselineProfiles)
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
//     -Pandroid.testInstrumentationRunnerArguments.class=com.zankup.android.baselineprofile.Benchmarks
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.zankup.android.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

baselineProfile {
    // The owner's phone, not a managed device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
