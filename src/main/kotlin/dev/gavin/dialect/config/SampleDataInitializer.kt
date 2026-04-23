package dev.gavin.dialect.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.Resource
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.queryForObject
import org.springframework.stereotype.Component

@Component
class SampleDataInitializer(
    private val jdbcOperations: JdbcOperations,
    private val sqlScriptRunner: SqlScriptRunner,
    @Value("classpath:sample-data.sql")
    private val sampleDataScript: Resource
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (sampleTablesExist()) {
            return
        }

        sqlScriptRunner.run(sampleDataScript)
    }

    internal fun sampleTablesExist(): Boolean {
        val tableCount = jdbcOperations.queryForObject<Int>(TABLE_COUNT_QUERY) ?: 0
        return tableCount == SAMPLE_TABLES.size
    }

    private companion object {
        private val SAMPLE_TABLES = listOf("users", "products", "orders", "order_items")

        private const val TABLE_COUNT_QUERY = """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN ('users', 'products', 'orders', 'order_items')
        """
    }
}
