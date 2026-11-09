pluginManagement {
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
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content {
                includeGroup("ai.pleos.playground")
                // TextToSpeech 3.3.1's published runtime dependency, same official repository.
                includeGroup("ai.pleos.playground.security")
            }
        }
    }
}

rootProject.name = "Surpride"
include(":app")
// Phase 1C-2: explicit opt-in keeps the normal SURPRIDE build unchanged.
if (providers.gradleProperty("navHostPoc").orNull == "true") {
    include(":nav-host-poc")
}
