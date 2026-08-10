package dev.bee.kanjianki.data.android

import dev.bee.kanjianki.data.sql.testing.SqlDriverContractSuite
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidFrameworkSqlDriverContractTest {
    private val contract = SqlDriverContractSuite(::AndroidFrameworkSqlDriver)

    @After
    fun tearDown() {
        contract.close()
    }

    @Test
    fun frameworkDriverPassesSharedContract() {
        contract.runAll()
    }
}
