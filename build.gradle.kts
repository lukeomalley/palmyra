plugins {
    alias(libs.plugins.android) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.7.1")
    }
}
