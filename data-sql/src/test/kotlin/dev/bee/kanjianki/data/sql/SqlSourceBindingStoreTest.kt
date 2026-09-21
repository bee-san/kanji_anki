package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.SourceBindingValidationState
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SqlSourceBindingStoreTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun bindingRoundTripsAndClears() {
        withDatabase { database ->
            val store = SqlSourceBindingStore(database)
            assertNull("no binding until one is saved", store.load())

            val binding = fixture(validatedAt = 1_000L)
            store.save(binding)
            assertEquals(binding, store.load())

            // Saving a replacement binding overwrites every record key.
            val replacement = fixture(providerDigest = "c".repeat(64), validatedAt = 2_000L)
            store.save(replacement)
            assertEquals(replacement, store.load())

            store.clear()
            assertNull("clear removes the whole record", store.load())
        }
    }

    @Test
    fun revalidationStateRoundTrips() {
        withDatabase { database ->
            val store = SqlSourceBindingStore(database)
            val binding = fixture(validationState = SourceBindingValidationState.REVALIDATION_REQUIRED)
            store.save(binding)
            assertEquals(SourceBindingValidationState.REVALIDATION_REQUIRED, store.load()?.validationState)
        }
    }

    private fun fixture(
        providerDigest: String = "a".repeat(64),
        validationState: SourceBindingValidationState = SourceBindingValidationState.VALIDATED,
        validatedAt: Long = 42L,
    ): PersistedSourceBinding =
        PersistedSourceBinding(
            version = PersistedSourceBinding.CURRENT_VERSION,
            providerKindDigest = providerDigest,
            sourceKeyDigest = "b".repeat(64),
            bindingSalt = "database-local-random-salt",
            noteIdDigests = listOf("d".repeat(64), "e".repeat(64)),
            cardIdDigests = listOf("f".repeat(64)),
            validationState = validationState,
            lastValidatedAtMillis = validatedAt,
        )

    private fun withDatabase(block: (DedicatedWriterSqlDatabase) -> Unit) {
        val path = temporaryDatabase()
        val database = DedicatedWriterSqlDatabase(
            driver = BundledTestSqlDriver(path.toString()),
            configuration = SqlDatabaseConfiguration(busyTimeoutMillis = 1_000, writerThreadName = "source-binding-test"),
        )
        try {
            runBlocking {
                SchemaManager(MigrationContext(clock = MigrationClock { 1L })).initialize(database)
            }
            block(database)
        } finally {
            database.close()
        }
    }

    private fun temporaryDatabase(): Path {
        val directory = Files.createTempDirectory("kani-source-binding-")
        temporaryDirectories.add(directory)
        return directory.resolve("kani.db")
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
