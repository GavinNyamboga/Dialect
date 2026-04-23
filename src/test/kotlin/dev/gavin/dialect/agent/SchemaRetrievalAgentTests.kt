package dev.gavin.dialect.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class SchemaRetrievalAgentTests {

    @Test
    fun `retrieves relevant tables for question keywords`() {
        val schemaAgent = Mockito.mock(SchemaAgent::class.java)
        val retrievalAgent = SchemaRetrievalAgent(schemaAgent)

        val result = retrievalAgent.selectRelevantCatalog(
            catalog = sampleCatalog(),
            question = "top products by revenue"
        )

        val selectedTables = result.columns
            .map { TableIdentifier(it.schemaName, it.tableName) }
            .toSet()

        assertTrue(TableIdentifier("public", "products") in selectedTables)
        assertTrue(TableIdentifier("public", "order_items") in selectedTables)
    }

    @Test
    fun `keeps connecting tables when question needs a join path`() {
        val schemaAgent = Mockito.mock(SchemaAgent::class.java)
        val retrievalAgent = SchemaRetrievalAgent(schemaAgent)

        val result = retrievalAgent.selectRelevantCatalog(
            catalog = sampleCatalog(),
            question = "customers by product"
        )

        val selectedTables = result.columns
            .map { TableIdentifier(it.schemaName, it.tableName) }
            .toSet()

        assertEquals(
            setOf(
                TableIdentifier("public", "users"),
                TableIdentifier("public", "orders"),
                TableIdentifier("public", "order_items"),
                TableIdentifier("public", "products")
            ),
            selectedTables
        )
    }

    @Test
    fun `formats retrieved catalog into schema text`() {
        val schemaAgent = Mockito.mock(SchemaAgent::class.java)
        val retrievalAgent = SchemaRetrievalAgent(schemaAgent)

        val schema = retrievalAgent.formatCatalog(
            SchemaCatalog(
                columns = listOf(
                    SchemaColumn("public", "users", "id", "bigint", false, null),
                    SchemaColumn("public", "users", "full_name", "text", false, null)
                ),
                foreignKeys = emptyList()
            )
        )

        assertEquals(
            """
            Database dialect: PostgreSQL
            Available tables:
            - public.users
              - id: bigint not null
              - full_name: text not null
            Relationships:
            - No foreign key relationships were found.
            """.trimIndent(),
            schema
        )
    }

    private fun sampleCatalog(): SchemaCatalog = SchemaCatalog(
        columns = listOf(
            SchemaColumn("public", "users", "id", "bigint", false, null),
            SchemaColumn("public", "users", "full_name", "text", false, null),
            SchemaColumn("public", "orders", "id", "bigint", false, null),
            SchemaColumn("public", "orders", "user_id", "bigint", false, null),
            SchemaColumn("public", "orders", "status", "text", false, null),
            SchemaColumn("public", "order_items", "id", "bigint", false, null),
            SchemaColumn("public", "order_items", "order_id", "bigint", false, null),
            SchemaColumn("public", "order_items", "product_id", "bigint", false, null),
            SchemaColumn("public", "order_items", "quantity", "integer", false, null),
            SchemaColumn("public", "products", "id", "bigint", false, null),
            SchemaColumn("public", "products", "name", "text", false, null),
            SchemaColumn("public", "products", "category", "text", false, null)
        ),
        foreignKeys = listOf(
            ForeignKeyRelationship("public", "orders", "user_id", "public", "users", "id"),
            ForeignKeyRelationship("public", "order_items", "order_id", "public", "orders", "id"),
            ForeignKeyRelationship("public", "order_items", "product_id", "public", "products", "id")
        )
    )
}
