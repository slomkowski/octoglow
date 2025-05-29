plugins {
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin
    application
    id("com.gradleup.shadow") version libs.versions.shadow.plugin
}

group = "eu.slomkowski.octoglow"
version = "1.0-SNAPSHOT"
description = "octoglowd"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass = "eu.slomkowski.octoglow.octoglowd.MainKt"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.org.jetbrains.kotlin.kotlin.stdlib.jdk8)
    implementation(libs.org.jetbrains.kotlin.kotlin.reflect)

    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.datetime.jvm)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json.jvm)

    implementation(libs.io.dvlopt.linux.i2c)

    implementation(libs.io.github.oshai.kotlin.logging.jvm)

    runtimeOnly(libs.ch.qos.logback.logback.classic)

    implementation(libs.org.jetbrains.exposed.exposed.core)
    implementation(libs.org.jetbrains.exposed.exposed.kotlin.datetime)
    runtimeOnly(libs.org.jetbrains.exposed.exposed.jdbc)

    implementation(libs.org.apache.commons.commons.lang3)

    implementation(libs.org.xerial.sqlite.jdbc)

    implementation(libs.io.ktor.ktor.client.cio.jvm)
    implementation(libs.io.ktor.ktor.client.content.negotiation.jvm)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json.jvm)

    testImplementation(libs.org.jetbrains.kotlin.kotlin.test.junit)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.engine)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
    testImplementation(libs.com.thedeanda.lorem)
    testImplementation(libs.org.assertj.assertj.core)
    testImplementation(libs.io.mockk.mockk.jvm)
    testImplementation(libs.de.jollyday.jollyday)
}

// todo call tests
tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveVersion = ""
    minimize {
        exclude(dependency("ch.qos.logback:logback-classic"))
        exclude(dependency("io.github.oshai:kotlin-logging-jvm"))

        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))

        exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm"))

        exclude(dependency("org.jetbrains.kotlinx:kotlinx-datetime-jvm"))

        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core"))

        exclude(dependency("org.xerial:sqlite-jdbc"))
        exclude(dependency("org.jetbrains.exposed:exposed-jdbc"))

        exclude(dependency("org.jetbrains.exposed:exposed-core"))
        exclude(dependency("org.jetbrains.exposed:exposed-kotlin-datetime"))

        exclude(dependency("io.ktor:ktor-client-cio-jvm"))
        exclude(dependency("io.ktor:ktor-client-content-negotiation-jvm"))
        exclude(dependency("io.ktor:ktor-serialization-kotlinx-json-jvm"))
    }
}