plugins {
    id("com.android.application")
}

val releaseStoreFile = providers.gradleProperty("APPHARBOR_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("APPHARBOR_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("APPHARBOR_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("APPHARBOR_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeType = "JKS"
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
