package io.heapy.kotbusta

import io.heapy.kotbusta.config.configureAuthentication
import io.heapy.kotbusta.config.configureCORS
import io.heapy.kotbusta.config.configureRouting
import io.heapy.kotbusta.config.configureSerialization
import io.heapy.kotbusta.database.DatabaseInitializer
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main() {
    EngineMain.main(arrayOf())
}

fun Application.module() {
    configureSerialization()
    configureCORS()
    configureAuthentication()
    
    DatabaseInitializer.initialize()
    
    configureRouting()
}