package dev.gavin.dialect

import dev.gavin.dialect.agent.ExecutionAgent
import dev.gavin.dialect.agent.LlmAgent
import dev.gavin.dialect.agent.PromptAgent
import dev.gavin.dialect.agent.QueryExecutionResult
import dev.gavin.dialect.agent.ValidationAgent
import dev.gavin.dialect.controller.QueryController
import dev.gavin.dialect.exception.GlobalExceptionHandler
import dev.gavin.dialect.model.SqlQueryResult
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.bean.override.mockito.MockitoBean

@WebMvcTest(QueryController::class)
@Import(GlobalExceptionHandler::class)
class DialectApplicationTests(
    @Autowired private val mockMvc: MockMvc
) {

    @MockitoBean
    private lateinit var promptAgent: PromptAgent

    @MockitoBean
    private lateinit var llmAgent: LlmAgent

    @MockitoBean
    private lateinit var validationAgent: ValidationAgent

    @MockitoBean
    private lateinit var executionAgent: ExecutionAgent

    @Test
    fun `returns success response envelope for valid query requests`() {
        val generatedQuery = SqlQueryResult(
            sql = " SELECT * FROM users; ",
            explanation = "List all users",
            dialect = "postgresql"
        )
        val validatedQuery = generatedQuery.copy(sql = "SELECT * FROM users")

        Mockito.`when`(promptAgent.buildPrompt("List all users")).thenReturn("prompt")
        Mockito.`when`(llmAgent.generateSql("prompt")).thenReturn(generatedQuery)
        Mockito.`when`(validationAgent.validate(generatedQuery)).thenReturn(validatedQuery)
        Mockito.`when`(executionAgent.execute("SELECT * FROM users")).thenReturn(
            QueryExecutionResult(
                rows = listOf(mapOf("id" to 1L, "email" to "one@example.com")),
                rowCount = 1,
                executionTimeMs = 12L
            )
        )

        mockMvc.perform(
            post("/api/queries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"List all users"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.question").value("List all users"))
            .andExpect(jsonPath("$.data.sql").value("SELECT * FROM users"))
            .andExpect(jsonPath("$.data.explanation").value("List all users"))
            .andExpect(jsonPath("$.data.dialect").value("postgresql"))
            .andExpect(jsonPath("$.data.rows[0].id").value(1))
            .andExpect(jsonPath("$.data.rows[0].email").value("one@example.com"))
            .andExpect(jsonPath("$.data.rowCount").value(1))
            .andExpect(jsonPath("$.data.executionTimeMs").value(12))
            .andExpect(jsonPath("$.timestamp").exists())

        Mockito.verify(promptAgent).buildPrompt("List all users")
        Mockito.verify(llmAgent).generateSql("prompt")
        Mockito.verify(validationAgent).validate(generatedQuery)
        Mockito.verify(executionAgent).execute("SELECT * FROM users")
    }

    @Test
    fun `returns structured bad request response when question is blank`() {
        mockMvc.perform(
            post("/api/queries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"   "}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Question must not be blank."))
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.path").value("/api/queries"))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `returns structured bad request response for malformed json`() {
        mockMvc.perform(
            post("/api/queries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"))
            .andExpect(jsonPath("$.error.message").value("Request body could not be parsed."))
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.path").value("/api/queries"))
            .andExpect(jsonPath("$.timestamp").exists())
    }
}
