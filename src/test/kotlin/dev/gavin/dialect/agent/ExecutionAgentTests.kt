package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.QueryExecutionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcOperations

class ExecutionAgentTests {

    @Test
    fun `executes sql and returns rows with metadata`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList("SELECT * FROM users")).thenReturn(
            listOf(
                mapOf("id" to 1L, "email" to "one@example.com"),
                mapOf("id" to 2L, "email" to null)
            )
        )

        val result = ExecutionAgent(jdbcOperations).execute("  SELECT * FROM users  ")

        assertEquals(2, result.rowCount)
        assertEquals(2, result.rows.size)
        assertEquals(1L, result.rows[0]["id"])
        assertEquals("one@example.com", result.rows[0]["email"])
        assertEquals(null, result.rows[1]["email"])
        Mockito.verify(jdbcOperations).queryForList("SELECT * FROM users")
    }

    @Test
    fun `rejects blank sql`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ExecutionAgent(jdbcOperations).execute("   ")
        }

        assertEquals("SQL must not be blank.", exception.message)
        Mockito.verifyNoInteractions(jdbcOperations)
    }

    @Test
    fun `wraps jdbc execution failures`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList("SELECT * FROM missing"))
            .thenThrow(RuntimeException("relation missing"))

        val exception = assertThrows(QueryExecutionException::class.java) {
            ExecutionAgent(jdbcOperations).execute("SELECT * FROM missing")
        }

        assertEquals("Failed to execute SQL query.", exception.message)
    }
}
