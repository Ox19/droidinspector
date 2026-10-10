pluginManagement {
    // Guarda: el build solo corre aislado (contenedor o CI), nunca en el host ni en el sync del IDE
    check(System.getenv("ISOLATED_BUILD") == "1" || System.getenv("GITHUB_ACTIONS") == "true") {
        "Build bloqueado fuera de aislamiento. Usa: docker compose run --rm prepare"
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DroidInspector"
include(":app")
