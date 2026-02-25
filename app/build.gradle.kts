plugins {
    id("com.android.application")
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riprog.launcher"
        minSdk = 23
        targetSdk = 35
        versionCode = 310
        versionName = "3.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signature/global.jks")
            storePassword = project.property("RELEASE_STORE_PASSWORD") as String
            keyAlias = project.property("RELEASE_KEY_ALIAS") as String
            keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    namespace = "com.riprog.launcher"
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
