plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.subgen.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.subgen.nativeapp.stable"
        minSdk = 29
        targetSdk = 34
        manifestPlaceholders["appLabel"] = "Subliminal Studio Native"
        versionCode = 15
        versionName = "3.2.1-audio-picker"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        debug { applicationIdSuffix = ".hypertest"; versionNameSuffix = "-test"; manifestPlaceholders["appLabel"] = "SubGen v3.2.1 Test" }
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").jniLibs.srcDirs("src/main/jniLibs")
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.tukaani:xz:1.10")
}
