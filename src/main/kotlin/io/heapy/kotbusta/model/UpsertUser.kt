package io.heapy.kotbusta.model

import io.heapy.kotbusta.ktor.GoogleUserInfo
import io.heapy.kotbusta.ktor.UserSession
import io.heapy.kotbusta.model.State.User
import io.heapy.kotbusta.model.State.UserId

class UpsertUser(
    private val googleUserInfo: GoogleUserInfo,
) : DatabaseOperation<User> {
    override fun process(state: ApplicationState): OperationResult<User> {
        val existingUser =
            state.users.values.find { it.googleId == googleUserInfo.id }

        return if (existingUser != null) {
            SuccessResult(state, existingUser)
        } else {
            val sequences = state.sequences
                .copy(userId = state.sequences.userId + 1)
            val newUser = User(
                id = UserId(sequences.userId),
                email = googleUserInfo.email,
                name = googleUserInfo.name,
                googleId = googleUserInfo.id,
                avatarUrl = googleUserInfo.avatarUrl,
                status = UserStatus.PENDING,
                kindleDevices = emptyList(),
                downloads = emptyList(),
                stars = emptyList(),
            )
            SuccessResult(
                state = state
                    .copy(
                        users = state.users + (newUser.id to newUser),
                        sequences = sequences,
                    ),
                result = newUser,
            )
        }
    }
}
