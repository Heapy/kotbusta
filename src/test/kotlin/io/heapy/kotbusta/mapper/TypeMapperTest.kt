package io.heapy.kotbusta.mapper

import io.heapy.kotbusta.dao.job.JobStatusMapper
import io.heapy.kotbusta.dao.job.JobTypeMapper
import io.heapy.kotbusta.jooq.enums.JobStatusEnum
import io.heapy.kotbusta.jooq.enums.JobTypeEnum
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType
import org.junit.jupiter.api.Test

class TypeMapperTest {
    @Test
    fun `JobTypeMapper should be bidirectional`() {
        JobTypeMapper.verifyBidirectional(
            inputs = JobType.entries,
            outputs = JobTypeEnum.entries.toList(),
        )
    }

    @Test
    fun `JobStatusMapper should be bidirectional`() {
        JobStatusMapper.verifyBidirectional(
            inputs = JobStatus.entries,
            outputs = JobStatusEnum.entries.toList(),
        )
    }
}