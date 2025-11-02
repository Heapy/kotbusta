package io.heapy.kotbusta.model

object Read : DatabaseOperation<Unit> {
    override fun process(state: ApplicationState): OperationResult<Unit> {
        return SuccessResult(state, Unit)
    }
}
