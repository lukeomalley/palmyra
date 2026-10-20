import java.util.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
}
val keyProperties = Properties()
val keyFile = rootProject.file("signing/keystore.properties")
if (keyFile.exists()) keyFile.inputStream().use { keyProperties.load(it) }
android {
    namespace = "xyz.lukeomalley.palmyra"
    compileSdk = 36
    defaultConfig {
        applicationId = "xyz.lukeomalley.palmyra"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        if (keyFile.exists()) {
            create("release") {
                storeFile = rootProject.file("signing/release.jks")
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = "palmyra"
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (keyFile.exists()) "release" else "debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        warningsAsErrors = true
        abortOnError = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { allWarningsAsErrors = true } }

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.roborazzi)
    testImplementation(libs.robolectric)
}
spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.7.1")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.7.1")
    }
    format("xml") {
        target("src/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
detekt {
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
}
tasks.register("checkRuntimeDependencies") {
    doLast {
        val allowed = setOf("org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains:annotations")
        configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.forEach {
            check(
                "${it.moduleVersion.id.group}:${it.name}" in allowed,
            ) { "Forbidden runtime dependency: ${it.moduleVersion.id}" }
        }
    }
}

// Asset generation changes test behavior, so it must participate in the task cache key.
tasks.withType<Test>().configureEach {
    inputs.property("generateBlurAssets", providers.environmentVariable("PALMYRA_GENERATE_BLURS").orElse("0"))
}
