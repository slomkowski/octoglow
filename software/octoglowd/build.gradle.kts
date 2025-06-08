plugins {
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin
    id("app.cash.sqldelight") version libs.versions.app.cash.sqldelight
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
//    implementation(libs.org.jetbrains.kotlin.kotlin.reflect)

    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.datetime.jvm)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json.jvm)

    implementation(libs.io.dvlopt.linux.i2c)

    implementation(libs.io.github.oshai.kotlin.logging.jvm)

    implementation(libs.org.tinylog.tinylog.api.kotlin)
    implementation(libs.org.tinylog.tinylog.impl)
    implementation(libs.org.tinylog.tinylog.slf4j)

    implementation(libs.org.apache.commons.commons.lang3)

    implementation(libs.io.ktor.ktor.client.cio.jvm)
    implementation(libs.io.ktor.ktor.client.content.negotiation.jvm)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json.jvm)

    implementation(libs.app.cash.sqldelight.sqlite.driver)

    testImplementation(libs.org.jetbrains.kotlin.kotlin.test.junit)
    testRuntimeOnly(libs.org.junit.platform.junit.platform.launcher)
    testImplementation(libs.org.junit.jupiter.junit.jupiter)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.engine)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
    testImplementation(libs.com.thedeanda.lorem)
    testImplementation(libs.org.assertj.assertj.core)
    testImplementation(libs.io.mockk.mockk.jvm)
    testImplementation(libs.de.jollyday.jollyday)
}

sqldelight {
    databases {
        create("SqlDelightDatabase") {
            packageName.set("eu.slomkowski.octoglow.octoglowd.db")
        }
    }
}

// todo call tests
tasks.test {
    useJUnitPlatform {
        excludeTags("hardware")
    }
}

tasks.shadowJar {
    archiveVersion = ""

    exclude("**/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    exclude("META-INF/maven")
    exclude("META-INF/*.map")
    exclude("META-INF/*.js")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/*.txt")

    //  exclude ("META-INF/**")


    exclude("org/sqlite/native/Windows/**")
    exclude("org/sqlite/native/FreeBSD/**")
    exclude("org/sqlite/native/Mac/**")
    exclude("org/sqlite/native/Linux-Android/**")
    exclude("org/sqlite/native/Linux-Musl/**")
    exclude("org/sqlite/native/Linux/aarch64/**")
    exclude("org/sqlite/native/Linux/android-arm/**")
    exclude("org/sqlite/native/Linux/arm/**")
    exclude("org/sqlite/native/Linux/armv6/**")
    exclude("org/sqlite/native/Linux/ppc64/**")
    exclude("org/sqlite/native/Linux/x86/**")
    exclude("org/sqlite/native/Linux/riscv64/**")

    exclude("com/sun/jna/aix*/**")
    exclude("com/sun/jna/darwin/**")
    exclude("com/sun/jna/freebsd*/**")
    exclude("com/sun/jna/openbsd*/**")
    exclude("com/sun/jna/sunos*/**")
    exclude("com/sun/jna/win32*/**")
    exclude("com/sun/jna/linux-aarch64/**")
    exclude("com/sun/jna/linux-armel/**")
    exclude("com/sun/jna/linux-mips64el/**")
    exclude("com/sun/jna/linux-ppc*/**")
    exclude("com/sun/jna/linux-s390x/**")
    exclude("com/sun/jna/linux-x86/**")


//    minimize {
//        exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm"))
//
//        exclude(dependency("org.jetbrains.kotlinx:kotlinx-datetime-jvm"))
//
//        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core"))
//
//        exclude(dependency("io.ktor:ktor-client-cio-jvm"))
//        exclude(dependency("io.ktor:ktor-client-content-negotiation-jvm"))
//        exclude(dependency("io.ktor:ktor-serialization-kotlinx-json-jvm"))
//    }
}