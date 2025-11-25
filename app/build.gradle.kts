plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // --- APP IDENTITY ---
    namespace = "kg.oshsu.myedu"
    compileSdk = 34

    defaultConfig {
        applicationId = "kg.oshsu.myedu"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // --- SIGNING CONFIGURATION ---
    signingConfigs {
        create("release") {
            // Read from Environment Variables (CI)
            val keystorePath = System.getenv("KEYSTORE_PATH")
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
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    // --- ANDROID CORE ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // --- COMPOSE UI (Material 3) ---
    implementation(platform("androidx.compose:compose-bom:2024.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // --- NETWORK & WEB ---
    implementation("androidx.webkit:webkit:1.8.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // --- IMAGE LOADING (Coil) ---
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0") 
    
    // --- LIFECYCLE ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
