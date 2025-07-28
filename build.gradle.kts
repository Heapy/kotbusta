plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.jooq:jooq:3.20.5")
    implementation("org.jooq:jooq-codegen:3.20.5")

    implementation("ch.qos.logback:logback-classic:1.5.18")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

application {
    mainClass.set("io.heapy.kotbusta.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.13.4")
        }
    }
}