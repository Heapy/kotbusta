package io.heapy.kotbusta.model

class ChangeUserStatus(
    private val userId: State.UserId,
    private val newStatus: UserStatus,
) : DatabaseOperation<Unit> {
    override fun process(state: ApplicationState): OperationResult<Unit> {
        val user = state.users[userId]

        return if (user != null) {
            val newUsers = state.users + (userId to user.copy(status = newStatus))
            SuccessResult(state.copy(newUsers), Unit)
        } else {
            ErrorResult("User $userId not found")
        }
    }
}
