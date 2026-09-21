package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.data.sql.testing.SqlDriverContractSuite
import org.junit.After
import org.junit.Test

class BundledSqlDriverContractTest {
    private val contract = SqlDriverContractSuite(::BundledTestSqlDriver)

    @After
    fun tearDown() {
        contract.close()
    }

    @Test
    fun bundledDriverPassesSharedContract() {
        contract.runAll()
    }
}
