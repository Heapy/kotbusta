package io.heapy.kotbusta.repository

import io.heapy.komok.tech.di.delegate.bean
import io.heapy.kotbusta.dao.book.*
import io.heapy.kotbusta.dao.job.*

class RepositoryModule {
    val getBooksQuery by bean {
        GetBooksQuery()
    }

    val searchBooksQuery by bean {
        SearchBooksQuery()
    }

    val getBookByIdQuery by bean {
        GetBookByIdQuery()
    }

    val getSimilarBooksQuery by bean {
        GetSimilarBooksQuery()
    }

    val getBookCoverQuery by bean {
        GetBookCoverQuery()
    }

    val starBookQuery by bean {
        StarBookQuery()
    }

    val unstarBookQuery by bean {
        UnstarBookQuery()
    }

    val getStarredBooksQuery by bean {
        GetStarredBooksQuery()
    }

    val bookRepository by bean {
        BookRepository(
            getBooksQuery = getBooksQuery.value,
            searchBooksQuery = searchBooksQuery.value,
            getBookByIdQuery = getBookByIdQuery.value,
            getSimilarBooksQuery = getSimilarBooksQuery.value,
            getBookCoverQuery = getBookCoverQuery.value,
            starBookQuery = starBookQuery.value,
            unstarBookQuery = unstarBookQuery.value,
            getStarredBooksQuery = getStarredBooksQuery.value,
        )
    }

    val updateProgressQuery by bean {
        UpdateProgressQuery()
    }

    val updateStatsQuery by bean {
        UpdateStatsQuery()
    }

    val completeJobQuery by bean {
        CompleteJobQuery()
    }

    val failJobQuery by bean {
        FailJobQuery()
    }

    val getAllJobsQuery by bean {
        GetAllJobsQuery()
    }

    val createImportJobQuery by bean {
        CreateImportJobQuery()
    }

    val importJobRepository by bean {
        ImportJobRepository(
            createImportJobQuery = createImportJobQuery.value,
            updateProgressQuery = updateProgressQuery.value,
            updateStatsQuery = updateStatsQuery.value,
            completeJobQuery = completeJobQuery.value,
            failJobQuery = failJobQuery.value,
            getAllJobsQuery = getAllJobsQuery.value,
        )
    }
}