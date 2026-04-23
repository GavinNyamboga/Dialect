package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.SchemaLoadException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcOperations

class SchemaAgentTests {

    @Test
    fun `formats schema metadata into prompt-friendly text`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList(Mockito.anyString())).thenReturn(
            listOf(
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "orders",
                    "column_name" to "id",
                    "data_type" to "uuid",
                    "is_nullable" to "NO",
                    "column_default" to "gen_random_uuid()"
                ),
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "orders",
                    "column_name" to "status",
                    "data_type" to "text",
                    "is_nullable" to "NO",
                    "column_default" to null
                ),
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "users",
                    "column_name" to "email",
                    "data_type" to "character varying",
                    "is_nullable" to "YES",
                    "column_default" to null
                )
            ),
            emptyList()
        )

        val agent = SchemaAgent(jdbcOperations)

        val schema = agent.loadSchema()

        assertEquals(
            """
            Database dialect: PostgreSQL
            Available tables:
            - public.orders
              - id: uuid not null default=gen_random_uuid()
              - status: text not null
            - public.users
              - email: character varying nullable
            Relationships:
            - No foreign key relationships were found.
            """.trimIndent(),
            schema
        )
    }

    @Test
    fun `includes foreign key relationships in schema context`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList(Mockito.anyString())).thenReturn(
            listOf(
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "orders",
                    "column_name" to "id",
                    "data_type" to "bigint",
                    "is_nullable" to "NO",
                    "column_default" to null
                ),
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "orders",
                    "column_name" to "user_id",
                    "data_type" to "bigint",
                    "is_nullable" to "NO",
                    "column_default" to null
                ),
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "users",
                    "column_name" to "id",
                    "data_type" to "bigint",
                    "is_nullable" to "NO",
                    "column_default" to null
                )
            ),
            listOf(
                mapOf(
                    "source_schema" to "public",
                    "source_table" to "orders",
                    "source_column" to "user_id",
                    "target_schema" to "public",
                    "target_table" to "users",
                    "target_column" to "id"
                )
            )
        )

        val agent = SchemaAgent(jdbcOperations)

        val schema = agent.loadSchema()

        assertEquals(
            """
            Database dialect: PostgreSQL
            Available tables:
            - public.orders
              - id: bigint not null
              - user_id: bigint not null
            - public.users
              - id: bigint not null
            Relationships:
            - public.orders.user_id -> public.users.id
            """.trimIndent(),
            schema
        )
    }

    @Test
    fun `uses cached schema until cache is cleared`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList(Mockito.anyString())).thenReturn(
            listOf(
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "users",
                    "column_name" to "id",
                    "data_type" to "bigint",
                    "is_nullable" to "NO",
                    "column_default" to null
                )
            ),
            emptyList(),
            listOf(
                mapOf(
                    "table_schema" to "public",
                    "table_name" to "users",
                    "column_name" to "id",
                    "data_type" to "bigint",
                    "is_nullable" to "NO",
                    "column_default" to null
                )
            ),
            emptyList()
        )

        val agent = SchemaAgent(jdbcOperations)

        val first = agent.loadSchema()
        val second = agent.loadSchema()

        assertEquals(first, second)
        Mockito.verify(jdbcOperations, Mockito.times(2)).queryForList(Mockito.anyString())

        agent.clearCache()
        agent.loadSchema()

        Mockito.verify(jdbcOperations, Mockito.times(4)).queryForList(Mockito.anyString())
    }

    @Test
    fun `wraps jdbc failures in schema load exception`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList(Mockito.anyString()))
            .thenThrow(RuntimeException("boom"))

        val agent = SchemaAgent(jdbcOperations)

        val exception = assertThrows(SchemaLoadException::class.java) {
            agent.loadSchema()
        }

        assertEquals("Failed to load database schema.", exception.message)
    }

    @Test
    fun `fails when database has no application tables`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        Mockito.`when`(jdbcOperations.queryForList(Mockito.anyString())).thenReturn(emptyList())

        val agent = SchemaAgent(jdbcOperations)

        val exception = assertThrows(SchemaLoadException::class.java) {
            agent.loadSchema()
        }

        assertEquals("No application tables were found in the database schema.", exception.message)
    }
}
