package dev.gavin.dialect.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.core.io.ByteArrayResource

class PromptAgentTests {

    @Test
    fun `builds prompt from schema retrieval agent and template resource`() {
        val schemaRetrievalAgent = Mockito.mock(SchemaRetrievalAgent::class.java)
        Mockito.`when`(schemaRetrievalAgent.retrieveSchema("List users"))
            .thenReturn("Database dialect: PostgreSQL\nAvailable tables:\n- public.users")

        val agent = PromptAgent(
            schemaRetrievalAgent = schemaRetrievalAgent,
            promptTemplate = ByteArrayResource(
                """
                Schema:
                <schema>

                Question:
                <question>
                """.trimIndent().toByteArray()
            )
        )

        val prompt = agent.buildPrompt("  List users  ")

        assertEquals(
            """
            Schema:
            Database dialect: PostgreSQL
            Available tables:
            - public.users

            Question:
            List users
            """.trimIndent(),
            prompt
        )
    }

    @Test
    fun `renders prompt by replacing question and schema placeholders`() {
        val schemaRetrievalAgent = Mockito.mock(SchemaRetrievalAgent::class.java)
        val agent = PromptAgent(schemaRetrievalAgent, ByteArrayResource(ByteArray(0)))

        val prompt = agent.renderPrompt(
            template = "Schema:\n<schema>\nQuestion:\n<question>",
            schema = "public.orders(id bigint)",
            question = "Count orders"
        )

        assertEquals(
            """
            Schema:
            public.orders(id bigint)
            Question:
            Count orders
            """.trimIndent(),
            prompt
        )
    }

    @Test
    fun `rejects blank questions before loading schema`() {
        val schemaRetrievalAgent = Mockito.mock(SchemaRetrievalAgent::class.java)
        val agent = PromptAgent(schemaRetrievalAgent, ByteArrayResource("template".toByteArray()))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            agent.buildPrompt("   ")
        }

        assertEquals("Question must not be blank.", exception.message)
        Mockito.verifyNoInteractions(schemaRetrievalAgent)
    }

    @Test
    fun `rejects blank templates`() {
        val schemaRetrievalAgent = Mockito.mock(SchemaRetrievalAgent::class.java)
        val agent = PromptAgent(schemaRetrievalAgent, ByteArrayResource(ByteArray(0)))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            agent.renderPrompt(template = "   ", question = "List users", schema = "public.users")
        }

        assertEquals("Prompt template must not be blank.", exception.message)
    }

    @Test
    fun `rejects blank schema`() {
        val schemaRetrievalAgent = Mockito.mock(SchemaRetrievalAgent::class.java)
        val agent = PromptAgent(schemaRetrievalAgent, ByteArrayResource(ByteArray(0)))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            agent.renderPrompt(template = "<schema> <question>", question = "List users", schema = "   ")
        }

        assertEquals("Schema must not be blank.", exception.message)
    }
}
