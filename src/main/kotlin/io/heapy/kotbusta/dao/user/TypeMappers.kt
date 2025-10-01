package io.heapy.kotbusta.dao.user

import io.heapy.kotbusta.jooq.enums.UserStatusEnum
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.model.UserStatus

val UserStatusMapper = TypeMapper<UserStatus, UserStatusEnum>(
    left = { input ->
        when (input) {
            UserStatus.PENDING -> UserStatusEnum.PENDING
            UserStatus.APPROVED -> UserStatusEnum.APPROVED
            UserStatus.REJECTED -> UserStatusEnum.REJECTED
            UserStatus.DEACTIVATED -> UserStatusEnum.DEACTIVATED
        }
    },
    right = { output ->
        when (output) {
            UserStatusEnum.PENDING -> UserStatus.PENDING
            UserStatusEnum.APPROVED -> UserStatus.APPROVED
            UserStatusEnum.REJECTED -> UserStatus.REJECTED
            UserStatusEnum.DEACTIVATED -> UserStatus.DEACTIVATED
        }
    }
)