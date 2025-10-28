import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jooq.core)
    implementation(libs.jooq.codegen)
    implementation(libs.flyway.core)
    api(libs.sqlite.jdbc)
    implementation(libs.komok.tech.config.dotenv)
    implementation(libs.logback.classic)
}

tasks.register<JavaExec>("migrate") {
    mainClass.set("GenerateJooqClasses")
    workingDir = rootDir
    classpath = sourceSets["main"].runtimeClasspath
}

java {
    targetCompatibility = JavaVersion.VERSION_24
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_24
    }
}
