// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
            // AGP 9.4.1 trae BouncyCastle 1.80.2 (alertas GHSA-9pwp-9qqc-pr26 y otras); se fuerza el fix
            listOf("bcprov", "bcpkix", "bcutil").forEach { module ->
                add("classpath", "org.bouncycastle:$module-jdk18on:1.85") {
                    because("Alertas de Dependabot en 1.80.2; AGP aún no sube la versión")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}