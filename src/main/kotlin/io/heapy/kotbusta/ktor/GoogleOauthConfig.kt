package io.heapy.kotbusta.ktor

data class GoogleOauthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
)
