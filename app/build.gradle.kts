import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Sürüm tek kaynaktan yönetilir: release.yml, etiketten türettiği sürümü
// -PappVersion=X.Y.Z olarak geçirir; yerel derlemeler alttaki varsayılanı
// kullanır. versionCode = major*10000 + minor*100 + patch.
val appVersion: String = (project.findProperty("appVersion") as? String) ?: "1.0.0"
val appVersionCode: Int = appVersion.split('.').map { it.toInt() }.let { (major, minor, patch) ->
    require(major < 214 && minor < 100 && patch < 100) { "Geçersiz sürüm: $appVersion" }
    // AGP, versionCode için pozitif tamsayı ister.
    (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}

android {
    namespace = "com.aripd.reyon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aripd.reyon"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // Release imzası CI'da ortam değişkenleriyle sağlanır (bkz. release.yml).
    // Değişkenler yoksa imzasız release üretilir; debug derlemeler etkilenmez.
    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    if (releaseKeystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "reyon"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                    ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Play, AAB'yi dile göre böler ve cihaza yalnız kendi dilini indirir.
    // Uygulama içi dil seçicisi bunu kaldırmadan çalışmaz: kullanıcı Almanca
    // seçtiğinde values-de cihazda bulunmalı. 14 dilin tüm metni ~340 KB ve
    // sıkışınca çok daha az — seçicinin çalışması bu bedeli hak ediyor.
    bundle {
        language {
            enableSplit = false
        }
    }

    // Arayüz testleri JVM'de Robolectric ile koşar (emülatör gerekmez); kaynaklar dahil edilir.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    // Compose metin ölçümü ve tuval çizimi için gerçek grafik yığını.
    systemProperty("robolectric.graphicsMode", "NATIVE")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // createComposeRule için ComponentActivity'yi debug manifestine ekler.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
