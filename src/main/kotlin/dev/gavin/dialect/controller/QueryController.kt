package dev.gavin.dialect.controller

import dev.gavin.dialect.agent.ExecutionAgent
import dev.gavin.dialect.agent.LlmAgent
import dev.gavin.dialect.agent.PromptAgent
import dev.gavin.dialect.agent.ValidationAgent
import dev.gavin.dialect.model.ApiResponse
import dev.gavin.dialect.model.QueryRequest
import dev.gavin.dialect.model.QueryResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/queries")
class QueryController(
    private val promptAgent: PromptAgent,
    private val llmAgent: LlmAgent,
    private val validationAgent: ValidationAgent,
    private val executionAgent: ExecutionAgent
) {

    @PostMapping
    fun executeQuery(@RequestBody request: QueryRequest): ResponseEntity<ApiResponse<QueryResponse>> {
        val question = request.question.trim()
        require(question.isNotEmpty()) { "Question must not be blank." }

        val prompt = promptAgent.buildPrompt(question)
        val generatedQuery = llmAgent.generateSql(prompt)
        val validatedQuery = validationAgent.validate(generatedQuery)
        val executionResult = executionAgent.execute(validatedQuery.sql)

        val response = QueryResponse(
            question = question,
            sql = validatedQuery.sql,
            explanation = validatedQuery.explanation,
            dialect = validatedQuery.dialect,
            rows = executionResult.rows,
            rowCount = executionResult.rowCount,
            executionTimeMs = executionResult.executionTimeMs
        )

        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
