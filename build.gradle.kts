// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
            // AGP 9.4.1 arrastra versiones con alertas de Dependabot; se fuerza la mínima parcheada
            // de cada una (no la última) para alejarse lo menos posible de lo que AGP probó.
            val patchedBuildDependencies = listOf(
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcutil-jdk18on:1.85",
                "org.apache.commons:commons-lang3:3.18.0",
                "org.jdom:jdom2:2.0.6.1",
                "org.bitbucket.b_c:jose4j:0.9.6",
            )
            patchedBuildDependencies.forEach { dependency ->
                add("classpath", dependency) {
                    because("Alerta de Dependabot en la versión que trae AGP")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}