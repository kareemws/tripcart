import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("luciq")
    id("luciq-apm")
}

// Secrets live in local.properties (never committed):
//   luciq.appToken=<Luciq app token>
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

        buildConfigField("String", "LUCIQ_APP_TOKEN", "\"${localProp("luciq.appToken")}\"")
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

luciq {
    networkInterception {
        enabled = true
        // Legacy interceptors are slated for removal; keep them off.
        okHttp {
            enabled = true
            legacyApmInterceptionEnabled = false
        }
        urlConnection {
            enabled = true
            legacyInterceptionEnabled = false
        }
    }
    apm {
        networkEnabled = true
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
    implementation(libs.luciq)
    implementation(libs.luciq.apm)
    implementation(libs.luciq.compose.apm)
    constraints {
        // luciq-core 19.12.1 pulls material 1.0.0 -> vectordrawable-animated 1.0.0, whose namespace
        // (androidx.vectordrawable) clashes with vectordrawable's and fails AGP 9's manifest merge.
        implementation("androidx.vectordrawable:vectordrawable-animated:1.1.0")
    }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}