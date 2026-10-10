plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.bouncy.castle)
    implementation(libs.okhttp)
    implementation("org.json:json:20240303")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bouncy.castle)
}
