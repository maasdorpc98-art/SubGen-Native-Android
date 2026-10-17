plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.subgen.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.subgen.nativeapp.original.fp32stability"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "3.0.4-fp32-stability-test"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
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
}
