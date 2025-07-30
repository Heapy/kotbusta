plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kotbusta"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.2.3")
        }
    }
}
