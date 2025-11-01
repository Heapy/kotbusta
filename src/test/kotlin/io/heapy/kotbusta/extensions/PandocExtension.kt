package io.heapy.kotbusta.extensions

import io.heapy.komok.tech.logging.Logger
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.TimeUnit

/**
 * JUnit 5 extension that conditionally enables tests based on Pandoc availability.
 *
 * Usage:
 * ```kotlin
 * @ExtendWith(PandocExtension::class)
 * class MyPandocTest {
 *     @Test
 *     fun `should convert files`() {
 *         // This test only runs if pandoc is installed
 *     }
 * }
 * ```
 *
 * Or use the annotation:
 * ```kotlin
 * @RequiresPandoc
 * class MyPandocTest { ... }
 * ```
 */
class PandocExtension : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        return if (isPandocAvailable()) {
            ConditionEvaluationResult.enabled("Pandoc is available")
        } else {
            ConditionEvaluationResult.disabled("Pandoc is not installed or not available in PATH")
        }
    }

    private fun isPandocAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("pandoc", "--version")
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            val exitCode = if (completed) process.exitValue() else -1

            if (!completed) {
                process.destroyForcibly()
            }

            completed && exitCode == 0
        } catch (e: Exception) {
            log.warn("Error while checking pandoc availability", e)
            false
        }
    }

    private companion object : Logger()
}

/**
 * Annotation that marks tests as requiring Pandoc to be installed.
 * Tests annotated with this will be skipped if Pandoc is not available.
 *
 * Can be applied to test classes or individual test methods.
 *
 * Example:
 * ```kotlin
 * @RequiresPandoc
 * @Test
 * fun `should convert FB2 to EPUB`() {
 *     // This test only runs if pandoc is available
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(PandocExtension::class)
annotation class RequiresPandoc
