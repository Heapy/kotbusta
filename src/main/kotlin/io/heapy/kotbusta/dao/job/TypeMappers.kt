package io.heapy.kotbusta.dao.job

import io.heapy.kotbusta.jooq.enums.JobStatusEnum
import io.heapy.kotbusta.jooq.enums.JobTypeEnum
import io.heapy.kotbusta.jooq.tables.records.ImportJobsRecord
import io.heapy.kotbusta.mapper.LeftTypeMapper
import io.heapy.kotbusta.mapper.TypeMapper
import io.heapy.kotbusta.mapper.mapUsing
import io.heapy.kotbusta.model.ImportJob
import io.heapy.kotbusta.model.JobStatus
import io.heapy.kotbusta.model.JobType

val JobTypeMapper = TypeMapper<JobType, JobTypeEnum>(
    left = { input ->
        when (input) {
            JobType.DATA_IMPORT -> JobTypeEnum.DATA_IMPORT
            JobType.COVER_EXTRACTION -> JobTypeEnum.COVER_EXTRACTION
        }
    },
    right = { output ->
        when (output) {
            JobTypeEnum.DATA_IMPORT -> JobType.DATA_IMPORT
            JobTypeEnum.COVER_EXTRACTION -> JobType.COVER_EXTRACTION
        }
    }
)

val JobStatusMapper = TypeMapper<JobStatus, JobStatusEnum>(
    left = { input ->
        when (input) {
            JobStatus.RUNNING -> JobStatusEnum.RUNNING
            JobStatus.COMPLETED -> JobStatusEnum.COMPLETED
            JobStatus.FAILED -> JobStatusEnum.FAILED
        }
    },
    right = { output ->
        when (output) {
            JobStatusEnum.RUNNING -> JobStatus.RUNNING
            JobStatusEnum.COMPLETED -> JobStatus.COMPLETED
            JobStatusEnum.FAILED -> JobStatus.FAILED
        }
    }
)

val ImportJobRecordMapper =
    LeftTypeMapper<ImportJobsRecord, ImportJob> { output ->
        ImportJob(
            id = output.id!!,
            jobType = output.jobType.mapUsing(JobTypeMapper),
            status = output.status.mapUsing(JobStatusMapper),
            progress = output.progress,
            inpFilesProcessed = output.inpFilesProcessed ?: 0,
            booksAdded = output.booksAdded ?: 0,
            booksUpdated = output.booksUpdated ?: 0,
            booksDeleted = output.booksDeleted ?: 0,
            coversAdded = output.coversAdded ?: 0,
            bookErrors = output.bookErrors ?: 0,
            coverErrors = output.coverErrors ?: 0,
            errorMessage = output.errorMessage,
            startedAt = output.startedAt!!.toEpochSecond(),
            completedAt = output.completedAt?.toEpochSecond(),
        )
    }
