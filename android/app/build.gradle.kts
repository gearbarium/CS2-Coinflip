plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}
android {
    namespace = "app.coinflip"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.coinflip"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("main").assets.srcDir("../../shared-spec")
        getByName("test").resources.srcDir("../../shared-spec")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    providers.environmentVariable("COINFLIP_KEYSTORE").orNull?.let { path ->
        val owner = signingConfigs.create("owner") {
            storeFile = file(path)
            storePassword = providers.environmentVariable("COINFLIP_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("COINFLIP_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("COINFLIP_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = owner
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
