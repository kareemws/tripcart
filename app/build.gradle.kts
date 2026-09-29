import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Ask AI config lives in local.properties (never committed):
//   ai.apiKey=sk-ant-...
//   ai.endpoint=https://api.anthropic.com/v1/messages   (optional)
//   ai.model=claude-opus-5                              (optional)
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localProp(key: String, default: String = "") = localProps.getProperty(key, default)

android {
    namespace = "com.legends.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.legends.myapplication"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "AI_ENDPOINT", "\"${localProp("ai.endpoint", "https://api.anthropic.com/v1/messages")}\"")
        buildConfigField("String", "AI_API_KEY", "\"${localProp("ai.apiKey")}\"")
        buildConfigField("String", "AI_MODEL", "\"${localProp("ai.model", "claude-opus-5")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}