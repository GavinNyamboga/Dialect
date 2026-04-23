package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.QueryExecutionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper

class LlmAgentTests {

    @Test
    fun `generates sql result from valid json response`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn(
            """{"sql":" SELECT * FROM users ","explanation":" List users ","dialect":" postgresql "}"""
        )

        val result = LlmAgent(chatCompletionClient, ObjectMapper()).generateSql(" prompt ")

        assertEquals("SELECT * FROM users", result.sql)
        assertEquals("List users", result.explanation)
        assertEquals("postgresql", result.dialect)
    }

    @Test
    fun `generates sql result from fenced json response`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn(
            """
            ```json
            {"sql":"SELECT count(*) FROM orders","explanation":"Count orders.","dialect":"postgresql"}
            ```
            """.trimIndent()
        )

        val result = LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("prompt")

        assertEquals("SELECT count(*) FROM orders", result.sql)
        assertEquals("Count orders.", result.explanation)
        assertEquals("postgresql", result.dialect)
    }

    @Test
    fun `generates sql result from json embedded in prose`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn(
            """
            Here is the corrected query:
            {"sql":"SELECT '{literal}' AS value","explanation":"Uses a string literal with braces.","dialect":"postgresql"}
            Done.
            """.trimIndent()
        )

        val result = LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("prompt")

        assertEquals("SELECT '{literal}' AS value", result.sql)
        assertEquals("Uses a string literal with braces.", result.explanation)
        assertEquals("postgresql", result.dialect)
    }

    @Test
    fun `cleans json embedded in prose`() {
        val agent = LlmAgent(Mockito.mock(ChatCompletionClient::class.java), ObjectMapper())

        val json = agent.cleanJson(
            """
            Prefix
            {"sql":"SELECT '{x}'","explanation":"ok","dialect":"postgresql"}
            Suffix
            """.trimIndent()
        )

        assertEquals(
            """{"sql":"SELECT '{x}'","explanation":"ok","dialect":"postgresql"}""",
            json
        )
    }

    @Test
    fun `generates sql result from response missing final object brace`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn(
            """
            {
              "sql": "SELECT id FROM users",
              "explanation": "Lists user ids.",
              "dialect": "postgresql"
            """.trimIndent()
        )

        val result = LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("prompt")

        assertEquals("SELECT id FROM users", result.sql)
        assertEquals("Lists user ids.", result.explanation)
        assertEquals("postgresql", result.dialect)
    }

    @Test
    fun `rejects blank prompt before calling llm`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("   ")
        }

        assertEquals("Prompt must not be blank.", exception.message)
        Mockito.verifyNoInteractions(chatCompletionClient)
    }

    @Test
    fun `wraps malformed json responses`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn("not json")

        val exception = assertThrows(QueryExecutionException::class.java) {
            LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("prompt")
        }

        assertEquals("Failed to generate SQL from LLM response.", exception.message)
    }

    @Test
    fun `wraps invalid structured responses`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn(
            """{"sql":"SELECT * FROM users","explanation":"","dialect":"postgresql"}"""
        )

        val exception = assertThrows(QueryExecutionException::class.java) {
            LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("prompt")
        }

        assertEquals("Failed to generate SQL from LLM response.", exception.message)
    }

    @Test
    fun `throws query execution exception for empty llm responses`() {
        val chatCompletionClient = Mockito.mock(ChatCompletionClient::class.java)
        Mockito.`when`(chatCompletionClient.complete("prompt")).thenReturn("   ")

        val exception = assertThrows(QueryExecutionException::class.java) {
            LlmAgent(chatCompletionClient, ObjectMapper()).generateSql("prompt")
        }

        assertEquals("LLM returned an empty response.", exception.message)
    }
}
