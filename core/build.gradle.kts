plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin: scoring rules, OCR parsing and stats that both the Android
// app and the leaderboard server depend on. No Android types allowed here,
// so all of it is unit-testable on the JVM.
kotlin {
    jvmToolchain(17)
}

dependencies {
    // Only for the wire models in core.api, shared with the server.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
