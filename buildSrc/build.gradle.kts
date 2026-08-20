plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
}

kotlin {
    jvmToolchain(21)
}
