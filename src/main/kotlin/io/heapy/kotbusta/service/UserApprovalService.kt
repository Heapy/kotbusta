package io.heapy.kotbusta.service

import io.heapy.kotbusta.dao.user.GetUserStatusQuery
import io.heapy.kotbusta.dao.user.ListPendingUsersQuery
import io.heapy.kotbusta.dao.user.UpdateUserStatusQuery
import io.heapy.kotbusta.dao.user.UserStatusMapper
import io.heapy.kotbusta.database.TransactionContext
import io.heapy.kotbusta.jooq.enums.UserStatusEnum
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.PendingUsersResponse
import io.heapy.kotbusta.model.UserStatus

class UserApprovalService(
    private val listPendingUsersQuery: ListPendingUsersQuery,
    private val updateUserStatusQuery: UpdateUserStatusQuery,
    private val getUserStatusQuery: GetUserStatusQuery,
) {
    context(_: TransactionContext)
    fun listPending(
        limit: Int = 20,
        offset: Int = 0
    ): PendingUsersResponse {
        val users = listPendingUsersQuery.listPending(limit, offset)
        val total = listPendingUsersQuery.countPending()
        val hasMore = (offset + limit) < total

        return PendingUsersResponse(
            users = users,
            total = total,
            hasMore = hasMore
        )
    }

    context(_: TransactionContext)
    fun approve(userId: Long): Boolean {
        return updateUserStatusQuery
            .updateStatus(userId, UserStatusEnum.APPROVED)
    }

    context(_: TransactionContext)
    fun reject(userId: Long): Boolean {
        return updateUserStatusQuery
            .updateStatus(userId, UserStatusEnum.REJECTED)
    }

    context(_: TransactionContext)
    fun deactivate(userId: Long): Boolean {
        return updateUserStatusQuery
            .updateStatus(userId, UserStatusEnum.DEACTIVATED)
    }

    context(_: TransactionContext)
    fun getUserStatus(userId: Long): UserStatus? {
        return getUserStatusQuery
            .getUserStatus(userId)
            ?.mapUsing(UserStatusMapper)
    }
}
