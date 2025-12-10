plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // --- APP IDENTITY ---
    namespace = "kg.oshsu.myedu"
    compileSdk = 36

    defaultConfig {
        applicationId = "kg.oshsu.myedu"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "1.3"
    }

    // --- SIGNING CONFIGURATION ---
    signingConfigs {
        create("release") {
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
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // --- BUILD FEATURES ---
    buildFeatures { 
        compose = true 
        buildConfig = true
    }
    
    // --- COMPILE OPTIONS ---
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// --- KOTLIN COMPILER OPTIONS ---
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }
}

dependencies {
    // --- ANDROID CORE (API 36 Support) ---
    implementation("androidx.core:core-ktx:1.15.0")
    
    // SPLASH SCREEN API (Required for Android 12+)
    implementation("androidx.core:core-splashscreen:1.0.1") 
    
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    
    // --- COMPOSE & MATERIAL 3 EXPRESSIVE ---
    val composeBom = platform("androidx.compose:compose-bom:2025.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.graphics:graphics-path:1.0.1")
    
    implementation("androidx.compose.material3:material3:1.5.0-alpha09")
    implementation("androidx.compose.material:material-icons-extended")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- NETWORK & WEB ---
    implementation("androidx.webkit:webkit:1.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    
    // --- IMAGE LOADING (Coil) ---
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0") 
    
    // WORK MANAGER (Required for Background Sync)
    implementation("androidx.work:work-runtime-ktx:2.10.0") 
}