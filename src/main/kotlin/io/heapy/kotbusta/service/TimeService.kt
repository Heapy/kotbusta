package io.heapy.kotbusta.service

import kotlin.time.Clock
import kotlin.time.Instant

interface TimeService {
    fun now(): Instant
}

class DefaultTimeService : TimeService {
    override fun now(): Instant = Clock.System.now()
}
