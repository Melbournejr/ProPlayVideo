import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

fun resolveSigningProp(key: String): String? {
    return System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
}

val dynamicVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 25
val dynamicVersionName = (project.findProperty("versionName") as String?) ?: "1.1.0"

android {
    namespace = "com.videoplayer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.videoplayer.app"
        minSdk = 24
        targetSdk = 34
        versionCode = dynamicVersionCode
        versionName = dynamicVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storePath = resolveSigningProp("SIGNING_STORE_FILE") ?: "ota-signing.jks"
            val storeFileObj = file(storePath)
            if (storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = resolveSigningProp("SIGNING_STORE_PASSWORD")
                keyAlias = resolveSigningProp("SIGNING_KEY_ALIAS")
                keyPassword = resolveSigningProp("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            val releaseSign = signingConfigs.findByName("release")
            if (releaseSign?.storeFile?.exists() == true && !releaseSign.storePassword.isNullOrBlank()) {
                signingConfig = releaseSign
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSign = signingConfigs.findByName("release")
            if (releaseSign?.storeFile?.exists() == true && !releaseSign.storePassword.isNullOrBlank()) {
                signingConfig = releaseSign
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
            excludes += "/META-INF/kotlinx_coroutines_core.version"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Chá»‰ giá»¯ material-icons-core, ÄÃƒ Bá»Ž material-icons-extended Ä‘á»ƒ giáº£m máº¡nh dung lÆ°á»£ng APK Release
    implementation("androidx.compose.material:material-icons-core")

    // Media3 (ExoPlayer)
    val media3Version = "1.2.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Biometric Authentication cho Kho RiÃªng TÆ° (Vault)
    implementation("androidx.biometric:biometric:1.1.0")

    // DataStore & Network
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
