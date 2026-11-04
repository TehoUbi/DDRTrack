pluginManagement {
    repositories {
        google()
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

rootProject.name = "DDRPerformanceTracker"
include(":core")
include(":server")
// The server's Docker build has no Android SDK, so it leaves the app (and
// the OCR engine only the app uses) out.
if (System.getenv("DDR_SERVER_ONLY") == null) {
    include(":ocr")
    include(":app")
}
