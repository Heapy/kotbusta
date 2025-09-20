package io.heapy.kotbusta.service

import io.heapy.kotbusta.ktor.UserSession

class AdminService(
    private val adminEmail: String?,
) {
    fun isAdmin(
        userSession: UserSession?,
    ): Boolean {
        return if (userSession != null) {
            userSession.email == adminEmail
        } else {
            false
        }
    }
}
