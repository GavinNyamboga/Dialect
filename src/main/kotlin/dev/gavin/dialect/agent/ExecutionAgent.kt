package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.QueryExecutionException
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class ExecutionAgent(
    private val jdbcOperations: JdbcOperations
) {
    private val logger = LoggerFactory.getLogger(ExecutionAgent::class.java)

    fun execute(sql: String): QueryExecutionResult {
        val normalizedSql = sql.trim()
        require(normalizedSql.isNotEmpty()) { "SQL must not be blank." }

        return try {
            var rows: List<Map<String, Any?>> = emptyList()
            val executionTimeMs = measureTimeMillis {
                rows = jdbcOperations.queryForList(normalizedSql)
            }

            QueryExecutionResult(
                rows = rows.map { row -> row.mapValues { it.value } },
                rowCount = rows.size,
                executionTimeMs = executionTimeMs
            )
        } catch (exception: Exception) {
            logger.error(
                "SQL execution failed. sql='{}'",
                normalizedSql,
                exception
            )
            throw QueryExecutionException("Failed to execute SQL query.", exception)
        }
    }
}

data class QueryExecutionResult(
    val rows: List<Map<String, Any?>>,
    val rowCount: Int,
    val executionTimeMs: Long
)
