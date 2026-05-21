plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.snipshot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.snipshot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val TRANSLATOR_URL: String by dotenv
        buildConfigField("String", "TRANSLATOR_URL", "\"$TRANSLATOR_URL\"")

        val SIMPLE_TRANSLATOR_URL: String by dotenv
        buildConfigField("String", "SIMPLE_TRANSLATOR_URL", "\"$SIMPLE_TRANSLATOR_URL\"")

        val SUPABASE_URL: String by dotenv
        buildConfigField("String", "SUPABASE_URL", "\"$SUPABASE_URL\"")

        val SUPABASE_ANON_KEY: String by dotenv
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$SUPABASE_ANON_KEY\"")

        val SUPABASE_STORAGE_BUCKET: String by dotenv
        buildConfigField("String", "SUPABASE_STORAGE_BUCKET", "\"$SUPABASE_STORAGE_BUCKET\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.volley)
    implementation(libs.androidx.cardview)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.okhttp3)
    implementation(libs.jetbrains)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // New dependencies for Dashboard, Cloud Storage, Settings
    implementation("io.coil-kt:coil:2.6.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
}

