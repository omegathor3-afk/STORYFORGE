plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedApplicationId = providers.gradleProperty("gameApplicationId")
    .orElse("com.storyforge.player").get()
val generatedDisplayName = providers.gradleProperty("gameDisplayName")
    .orElse("STORYFORGE PLAYER").get()

require(generatedApplicationId.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*){1,5}"))) {
    "gameApplicationId must be a valid Android application ID"
}
require(generatedDisplayName.isNotBlank() && generatedDisplayName.length <= 40) {
    "gameDisplayName must contain 1-40 characters"
}

android {
    namespace = "com.storyforge.player"
    compileSdk = 35
    defaultConfig {
        applicationId = generatedApplicationId
        manifestPlaceholders["gameDisplayName"] = generatedDisplayName
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
