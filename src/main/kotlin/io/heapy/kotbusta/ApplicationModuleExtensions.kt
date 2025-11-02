package io.heapy.kotbusta

import io.heapy.kotbusta.model.DatabaseOperation
import io.heapy.kotbusta.model.OperationResult

suspend fun <T> ApplicationModule.run(
    operation: DatabaseOperation<T>,
): OperationResult<T> =
    database.value.run(operation)
