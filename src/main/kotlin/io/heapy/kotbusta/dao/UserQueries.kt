package io.heapy.kotbusta.dao

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State
import io.heapy.kotbusta.model.UserInfo
import io.heapy.kotbusta.model.getUsers

context(_: ApplicationModule)
suspend fun validateUserSession(
    userId: State.UserId,
): Boolean = getUsers()[userId] != null

context(_: ApplicationModule, userSession: UserSession)
suspend fun getUserInfo(): UserInfo? {
    return getUsers()[userSession.userId]
        ?.toUserInfo()
}

fun State.User.toUserInfo() = UserInfo(
    userId = id,
    email = email,
    name = name,
    status = status,
    isAdmin = isAdmin,
    avatarUrl = avatarUrl,
)
