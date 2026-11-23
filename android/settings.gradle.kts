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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FocusLite"

// Pure Kotlin (no Android imports). Unit-tested on the JVM against screen snapshots.
include(":core:model")
include(":engine:detection")
include(":engine:policy")
include(":engine:usage")

// Android
include(":engine:enforcement")
include(":engine:accessibility")
include(":app")
