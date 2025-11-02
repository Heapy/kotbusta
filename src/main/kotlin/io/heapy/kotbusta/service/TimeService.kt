package io.heapy.kotbusta.service

import java.time.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

interface TimeService {
    fun now(): Instant
    fun dateNow(): LocalDate
}

class DefaultTimeService : TimeService {
    override fun now(): Instant = Clock.System.now()
    override fun dateNow(): LocalDate = LocalDate.now()
}
