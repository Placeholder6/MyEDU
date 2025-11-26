plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Add this line
}

android {
    // --- APP IDENTITY ---
    namespace = "kg.oshsu.myedu"
    compileSdk = 36

    defaultConfig {
        applicationId = "kg.oshsu.myedu"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.2"
    }

    // --- SIGNING CONFIGURATION ---
    signingConfigs {
        create("release") {
            // Read from Environment Variables (CI)
            val keystorePath = System.getenv("RELEASE_STORE_FILE")
            val storePass = System.getenv("RELEASE_STORE_PASSWORD")
            val keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")

            if (keystorePath != null && storePass != null && keyAlias != null && keyPass != null) {
                storeFile = file(keystorePath)
                storePassword = storePass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            // Only sign if config was successfully created
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // --- BUILD FEATURES ---
    buildFeatures { compose = true }
    
    // --- KOTLIN OPTIONS ---
    compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17 // Was VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_17 // Was VERSION_1_8
    }
    kotlinOptions { jvmTarget = "17" } // Was "1.8"
}

dependencies {
    // --- ANDROID CORE ---
    implementation("androidx.core:core-ktx:1.17.0") // Updated from 1.12.0
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0") // Updated from 2.7.0
    implementation("androidx.activity:activity-compose:1.12.0") // Updated from 1.8.2 (Required for API 36)

    // --- COMPOSE UI (Material 3) ---
    // Update BOM to 2025.11.00
    implementation(platform("androidx.compose:compose-bom:2025.11.00")) 
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // --- NETWORK & WEB ---
    implementation("androidx.webkit:webkit:1.12.0") // Update recommended for API 36
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // --- IMAGE LOADING (Coil) ---
    implementation("io.coil-kt:coil-compose:2.7.0") // Updated from 2.4.0
    implementation("io.coil-kt:coil-svg:2.7.0") 

    // --- LIFECYCLE ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0") // Updated from 2.7.0
}