package io.heapy.kotbusta.config

data class GoogleOauthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
)
