package io.heapy.kotbusta.ktor

import io.heapy.kotbusta.ApplicationModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmCompilationMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics

context(applicationModule: ApplicationModule)
fun Application.configureMetrics() {
    install(MicrometerMetrics) {
        registry = applicationModule.prometheusRegistry.value
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmCompilationMetrics(),
            JvmGcMetrics(),
            JvmMemoryMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            UptimeMetrics(),
        )
    }
}
