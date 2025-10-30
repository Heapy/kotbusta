package io.heapy.kotbusta.dao

import io.heapy.kotbusta.database.TransactionProvider
import io.heapy.kotbusta.database.transaction
import io.heapy.kotbusta.database.useTx
import io.heapy.kotbusta.jooq.tables.references.SERIES
import io.heapy.kotbusta.test.DatabaseExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Integration tests for SeriesQueries.
 *
 * Tests database query methods that use useTx for series-related operations.
 */
@ExtendWith(DatabaseExtension::class)
class SeriesQueriesTest {
    @Test
    context(_: TransactionProvider)
    fun `insertOrGetSeries should return existing series ID when series exists`() = transaction {
        // Given: "Harry Potter" series exists in test fixtures with ID 1
        val seriesName = "Harry Potter"

        // When: Trying to insert existing series
        val seriesId = insertOrGetSeries(seriesName)

        // Then: Should return existing ID
        assertEquals(1, seriesId)

        // Verify no duplicate was created
        val count = useTx { dslContext ->
            dslContext
                .selectCount()
                .from(SERIES)
                .where(SERIES.NAME.eq("Harry Potter"))
                .fetchOne(0, Int::class.java)
        }
        assertEquals(1, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetSeries should insert new series and return ID`() = transaction {
        // Given: New series not in fixtures
        val seriesName = "The Wheel of Time"

        // When: Inserting new series
        val seriesId = insertOrGetSeries(seriesName)

        // Then: Should return new ID
        assertNotNull(seriesId)

        // Verify series was inserted
        val savedSeries = useTx { dslContext ->
            dslContext
                .selectFrom(SERIES)
                .where(SERIES.ID.eq(seriesId))
                .fetchOne()
        }

        assertNotNull(savedSeries)
        assertEquals("The Wheel of Time", savedSeries?.name)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetSeries should be idempotent`() = transaction {
        // Given: A new series name
        val seriesName = "The Kingkiller Chronicle"

        // When: Inserting the same series twice
        val firstId = insertOrGetSeries(seriesName)
        val secondId = insertOrGetSeries(seriesName)

        // Then: Should return the same ID both times
        assertEquals(firstId, secondId)

        // Verify only one record exists
        val count = useTx { dslContext ->
            dslContext
                .selectCount()
                .from(SERIES)
                .where(SERIES.NAME.eq("The Kingkiller Chronicle"))
                .fetchOne(0, Int::class.java)
        }
        assertEquals(1, count)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetSeries should handle series names with special characters`() = transaction {
        // Given: Series name with apostrophe
        val seriesName = "Hitchhiker's Guide"

        // When: Inserting series with special character
        val seriesId = insertOrGetSeries(seriesName)

        // Then: Should insert successfully
        assertNotNull(seriesId)

        val savedSeries = useTx { dslContext ->
            dslContext
                .selectFrom(SERIES)
                .where(SERIES.ID.eq(seriesId))
                .fetchOne()
        }

        assertEquals("Hitchhiker's Guide", savedSeries?.name)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetSeries should distinguish between similar names`() = transaction {
        // Given: Two similar but different series names
        val series1 = "Foundation"
        val series2 = "Foundation Prequels"

        // When: Inserting both series
        val id1 = insertOrGetSeries(series1)
        val id2 = insertOrGetSeries(series2)

        // Then: Should have different IDs
        assertEquals(4, id1) // "Foundation" exists in fixtures with ID 4
        assertNotNull(id2)

        // Verify both series exist
        val foundationPrequels = useTx { dslContext ->
            dslContext
                .selectFrom(SERIES)
                .where(SERIES.ID.eq(id2))
                .fetchOne()
        }
        assertEquals("Foundation Prequels", foundationPrequels?.name)
    }

    @Test
    context(_: TransactionProvider)
    fun `insertOrGetSeries should handle all series from fixtures`() = transaction {
        // Given: Test fixtures contain 4 series
        // When: Getting all existing series
        val harryPotterId = insertOrGetSeries("Harry Potter")
        val songOfIceId = insertOrGetSeries("A Song of Ice and Fire")
        val stormlightId = insertOrGetSeries("The Stormlight Archive")
        val foundationId = insertOrGetSeries("Foundation")

        // Then: Should return existing IDs
        assertEquals(1, harryPotterId)
        assertEquals(2, songOfIceId)
        assertEquals(3, stormlightId)
        assertEquals(4, foundationId)
    }
}
