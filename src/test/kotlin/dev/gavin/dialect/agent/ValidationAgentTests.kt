package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.SqlValidationException
import dev.gavin.dialect.model.SqlQueryResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class ValidationAgentTests {

    private val agent = ValidationAgent()

    @Test
    fun `accepts select statements and trims trailing semicolon`() {
        val sql = agent.validateSql("  SELECT id, email FROM users;  ")

        assertEquals("SELECT id, email FROM users", sql)
    }

    @Test
    fun `accepts select statements with comments`() {
        val sql = agent.validateSql(
            """
            -- generated query
            SELECT id
            FROM users /* public customers */
            """.trimIndent()
        )

        assertEquals("SELECT id\nFROM users", sql)
    }

    @Test
    fun `validates complete sql query result`() {
        val result = agent.validate(
            SqlQueryResult(
                sql = "SELECT count(*) FROM orders;",
                explanation = "Count orders",
                dialect = "PostgreSQL"
            )
        )

        assertEquals("SELECT count(*) FROM orders", result.sql)
        assertEquals("PostgreSQL", result.dialect)
    }

    @Test
    fun `rejects blank sql`() {
        val exception = assertThrows(SqlValidationException::class.java) {
            agent.validateSql("   ")
        }

        assertEquals("SQL must not be blank.", exception.message)
    }

    @Test
    fun `rejects non select statements`() {
        val exception = assertThrows(SqlValidationException::class.java) {
            agent.validateSql("DELETE FROM users")
        }

        assertEquals("Only SELECT statements are allowed.", exception.message)
    }

    @Test
    fun `rejects mutation keyword inside select`() {
        val exception = assertThrows(SqlValidationException::class.java) {
            agent.validateSql("SELECT * FROM users; DROP TABLE users;")
        }

        assertEquals("Only a single SQL statement is allowed.", exception.message)
    }

    @Test
    fun `rejects forbidden operations in common table expressions`() {
        val exception = assertThrows(SqlValidationException::class.java) {
            agent.validateSql("SELECT * FROM users WHERE id IN (DELETE FROM sessions RETURNING user_id)")
        }

        assertEquals("SQL contains a forbidden operation.", exception.message)
    }

    @Test
    fun `rejects multiple statements`() {
        val exception = assertThrows(SqlValidationException::class.java) {
            agent.validateSql("SELECT * FROM users; SELECT * FROM orders")
        }

        assertEquals("Only a single SQL statement is allowed.", exception.message)
    }

    @Test
    fun `rejects unsupported dialect`() {
        val exception = assertThrows(SqlValidationException::class.java) {
            agent.validate(
                SqlQueryResult(
                    sql = "SELECT * FROM users",
                    explanation = "List users",
                    dialect = "mysql"
                )
            )
        }

        assertEquals("Only PostgreSQL SQL is supported.", exception.message)
    }

    @Test
    fun `rejects alias column references that do not exist on the aliased table`() {
        val schemaAgent = Mockito.mock(SchemaAgent::class.java)
        Mockito.`when`(schemaAgent.loadCatalog()).thenReturn(sampleCatalog())
        val catalogAwareAgent = ValidationAgent(schemaAgent)

        val exception = assertThrows(SqlValidationException::class.java) {
            catalogAwareAgent.validateSql(
                """
                SELECT oi.user_id
                FROM public.order_items oi
                JOIN public.orders o ON oi.order_id = o.id
                """.trimIndent()
            )
        }

        assertEquals(
            "Column 'user_id' does not exist on table 'public.order_items' aliased as 'oi'.",
            exception.message
        )
    }

    @Test
    fun `accepts alias column references that exist on their aliased tables`() {
        val schemaAgent = Mockito.mock(SchemaAgent::class.java)
        Mockito.`when`(schemaAgent.loadCatalog()).thenReturn(sampleCatalog())
        val catalogAwareAgent = ValidationAgent(schemaAgent)

        val sql = catalogAwareAgent.validateSql(
            """
            SELECT o.user_id, oi.quantity
            FROM public.order_items oi
            JOIN public.orders o ON oi.order_id = o.id
            ORDER BY oi.quantity DESC
            LIMIT 1
            """.trimIndent()
        )

        assertEquals(
            """
            SELECT o.user_id, oi.quantity
            FROM public.order_items oi
            JOIN public.orders o ON oi.order_id = o.id
            ORDER BY oi.quantity DESC
            LIMIT 1
            """.trimIndent(),
            sql
        )
    }

    private fun sampleCatalog(): SchemaCatalog = SchemaCatalog(
        columns = listOf(
            SchemaColumn(
                schemaName = "public",
                tableName = "order_items",
                columnName = "order_id",
                dataType = "bigint",
                nullable = false,
                defaultValue = null
            ),
            SchemaColumn(
                schemaName = "public",
                tableName = "order_items",
                columnName = "quantity",
                dataType = "integer",
                nullable = false,
                defaultValue = null
            ),
            SchemaColumn(
                schemaName = "public",
                tableName = "orders",
                columnName = "id",
                dataType = "bigint",
                nullable = false,
                defaultValue = null
            ),
            SchemaColumn(
                schemaName = "public",
                tableName = "orders",
                columnName = "user_id",
                dataType = "bigint",
                nullable = false,
                defaultValue = null
            )
        ),
        foreignKeys = emptyList()
    )
}
