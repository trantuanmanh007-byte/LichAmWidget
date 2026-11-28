plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNum = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.licham.widget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.licham.widget"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNum + 10
        versionName = "1.$buildNum"
    }

    signingConfigs {
     create("release") {
         storeFile = file("widget_signing.jks")
         storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
         keyAlias = "lichamwidget"
         keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
     }
 }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
