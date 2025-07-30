package io.heapy.kotbusta.coroutines

import infra.coroutines.Loom
import io.heapy.komok.tech.di.delegate.bean
import kotlinx.coroutines.Dispatchers

class DispatchersModule {
    val ioDispatcher by bean {
        Dispatchers.Loom
    }

    val defaultDispatcher by bean {
        Dispatchers.Default
    }
}
