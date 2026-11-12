plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.clashtimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.clashtimer"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Kunci tanda tangan tetap (kunci & sandinya dari GitHub Secrets, lihat README), supaya APK versi baru
    // bisa langsung di-install di atas versi lama tanpa harus uninstall dulu.
    val signPass: String? = System.getenv("CT_STORE_PASSWORD")
    signingConfigs {
        create("fixed") {
            storeFile = file(System.getenv("CT_KEYSTORE_FILE") ?: "clashtimer.keystore")
            storePassword = signPass
            keyAlias = System.getenv("CT_KEY_ALIAS") ?: "clashtimer"
            keyPassword = System.getenv("CT_KEY_PASSWORD") ?: signPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signPass != null) signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            if (signPass != null) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Di GitHub Actions (CI=true) APK rilis baru dibuat setelah pemeriksaan data game & tes perencana lolos.
// Bila gagal, build berhenti dan tidak ada rilis baru; tes yang gagal tampil di log Actions.
if (System.getenv("CI") != null) {
    val validateGameData = tasks.register<Exec>("validateGameData") {
        workingDir = rootProject.projectDir
        commandLine("python3", "tools/build_gamedata.py", "--validate")
    }
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        dependsOn(validateGameData, "testReleaseUnitTest")
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Tes inti perencana (app/src/test): wajib lolos sebelum APK rilis dibuat di GitHub Actions.
    testImplementation("junit:junit:4.13.2")
}
