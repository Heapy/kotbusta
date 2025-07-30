plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jooq.core)
    implementation(libs.jooq.codegen)
    implementation(libs.flyway.database.postgresql)
    api(libs.postgresql)
    implementation(libs.komok.tech.config.dotenv)
    implementation(libs.logback.classic)
}
