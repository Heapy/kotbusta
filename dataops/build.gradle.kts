plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jooq.core)
    implementation(libs.jooq.codegen)
    api(libs.sqlite.jdbc)
    implementation(libs.komok.tech.logging)
    implementation(libs.komok.tech.config.dotenv)
    implementation(libs.logback.classic)
}

tasks.register<JavaExec>("migrate") {
    mainClass.set("GenerateJooqClasses")
    workingDir = rootDir
    classpath = sourceSets["main"].runtimeClasspath
}

kotlin {
    jvmToolchain(25)
}
