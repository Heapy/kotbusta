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

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.aws.sdk.ses)

    testImplementation(ktorLibs.server.testHost)
}

application {
    applicationName = "kotbusta"
    mainClass.set("io.heapy.kotbusta.Application")
    applicationDefaultJvmArgs = listOf()
}

tasks.distTar {
    archiveFileName.set("kotbusta.tar")
}

tasks.distZip {
    enabled = false
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
        )
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit)
        }
    }
}
