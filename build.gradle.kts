plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":dataops"))

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
    implementation(libs.slf4j.jul.to.slf4j)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.aws.sdk.sesv2)
    implementation(libs.lucene.core)
    implementation(libs.lucene.analysis.common)
    implementation(libs.lucene.queryparser)
    implementation(libs.djl.api)
    implementation(libs.djl.huggingface.tokenizers)
    runtimeOnly(libs.djl.onnxruntime.engine)
    implementation(libs.micrometer.registry.prometheus)
    implementation(ktorLibs.server.metrics.micrometer)
    implementation(libs.jsoup)

    testImplementation(ktorLibs.server.testHost)
}

application {
    applicationName = "kotbusta"
    mainClass.set("io.heapy.kotbusta.Application")
    applicationDefaultJvmArgs = listOf(
        "--add-modules=jdk.incubator.vector",
        "-Dai.djl.offline=true",
        "-DENGINE_CACHE_DIR=.djl/engine-cache",
    )
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

tasks.test {
    jvmArgs("--add-modules=jdk.incubator.vector")
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
