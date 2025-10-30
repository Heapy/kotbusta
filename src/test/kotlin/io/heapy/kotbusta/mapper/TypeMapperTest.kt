package io.heapy.kotbusta.mapper

import io.heapy.kotbusta.dao.JobStatusMapper
import io.heapy.kotbusta.dao.JobTypeMapper
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import org.junit.jupiter.api.Test

class TypeMapperTest {
    @Test
    fun `JobTypeMapper should be bidirectional`() {
        JobTypeMapper.verifyBidirectional(
            inputs = JobType.entries,
            outputs = listOf("DATA_IMPORT", "COVER_EXTRACTION"),
        )
    }

    @Test
    fun `JobStatusMapper should be bidirectional`() {
        JobStatusMapper.verifyBidirectional(
            inputs = JobStatus.entries,
            outputs = listOf("RUNNING", "COMPLETED", "FAILED"),
        )
    }
}
