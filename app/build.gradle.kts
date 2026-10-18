plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.subgen.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.subgen.nativeapp.stable"
        minSdk = 29
        targetSdk = 34
        versionCode = 13
        versionName = "3.1.3-neuromodulation-full-ab"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        debug { applicationIdSuffix = ".abtest"; versionNameSuffix = "-test" }
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