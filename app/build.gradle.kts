plugins {
    id("com.android.application")
}

android {
    namespace = "dev.appharbor"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.appharbor.mobile"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
