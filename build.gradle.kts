plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":dataops"))
    implementation(libs.hikari)

    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)


    implementation(libs.komok.tech.config.dotenv)
    implementation(libs.komok.tech.to.be.injected)
    implementation(libs.komok.tech.logging)
    implementation(libs.logback.classic)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(ktorLibs.server.testHost)
}

application {
    applicationName = "kotbusta"
    mainClass.set("io.heapy.kotbusta.ApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
    )
}

tasks.distTar {
    archiveFileName.set("kotbusta.tar")
}

tasks.distZip {
    enabled = false
}

kotlin {
    jvmToolchain(24)

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.13.4")
        }
    }
}
