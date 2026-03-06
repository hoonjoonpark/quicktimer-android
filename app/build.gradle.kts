import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

fun loadPropertiesFile(path: String): Properties {
    val props = Properties()
    val file = rootProject.file(path)
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    return props
}

val projectLocalProps = loadPropertiesFile("local.properties")
val projectGradleProps = loadPropertiesFile("gradle.properties")

fun propFromProjectOrEnv(name: String): String? {
    val localValue = projectLocalProps.getProperty(name)?.trim()
    if (!localValue.isNullOrEmpty()) return localValue
    val projectValue = projectGradleProps.getProperty(name)?.trim()
    if (!projectValue.isNullOrEmpty()) return projectValue
    val envValue = System.getenv(name)?.trim()
    if (!envValue.isNullOrEmpty()) return envValue
    return null
}

val testBannerAdUnit = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialAdUnit = "ca-app-pub-3940256099942544/1033173712"

val devBannerAdUnit = propFromProjectOrEnv("ADMOB_DEV_BANNER_UNIT_ID") ?: testBannerAdUnit
val devInterstitialAdUnit = propFromProjectOrEnv("ADMOB_DEV_INTERSTITIAL_UNIT_ID") ?: testInterstitialAdUnit
val prodBannerAdUnit = propFromProjectOrEnv("ADMOB_PROD_BANNER_UNIT_ID") ?: testBannerAdUnit
val prodInterstitialAdUnit = propFromProjectOrEnv("ADMOB_PROD_INTERSTITIAL_UNIT_ID") ?: testInterstitialAdUnit

android {
    namespace = "com.quicktimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quicktimer"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("String", "AD_UNIT_BANNER", "\"$devBannerAdUnit\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$devInterstitialAdUnit\"")
        }
        create("prod") {
            dimension = "env"
            // Defaults to test ad units until production IDs are provided via gradle properties/env.
            buildConfigField("String", "AD_UNIT_BANNER", "\"$prodBannerAdUnit\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$prodInterstitialAdUnit\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.register("assembleProdRel") {
    group = "build"
    description = "Assemble the prodRelease variant."
    dependsOn("assembleProdRelease")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.gms:play-services-ads:23.5.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
