import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing comes from android/keystore.properties locally, or LANTERN_* environment variables in CI.
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun sign(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

// Version comes from the release tag (LANTERN_VERSION=1.3.0), falling back to a dev build.
val versionNameValue = System.getenv("LANTERN_VERSION") ?: "0.0.0-dev"
val versionCodeValue = versionNameValue.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (a, b, c) -> a * 10000 + b * 100 + c }
    .coerceAtLeast(1)

android {
    namespace = "com.orbit.lantern"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.orbit.lantern"
        minSdk = 23
        targetSdk = 35
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    signingConfigs {
        create("release") {
            val store = sign("storeFile", "LANTERN_KEYSTORE")
            if (store != null) {
                storeFile = file(store)
                storePassword = sign("storePassword", "LANTERN_KEYSTORE_PASSWORD")
                keyAlias = sign("keyAlias", "LANTERN_KEY_ALIAS")
                keyPassword = sign("keyPassword", "LANTERN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (sign("storeFile", "LANTERN_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Bundle the web interface from the repo root into the app.
val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../index.html"))
    into(layout.projectDirectory.dir("src/main/assets/www"))
}
tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    // Native IPTV player (MPEG-TS, HLS and most file formats)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
