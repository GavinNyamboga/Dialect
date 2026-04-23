package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.QueryExecutionException
import dev.gavin.dialect.model.SqlQueryResult
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class LlmAgent(
    private val chatCompletionClient: ChatCompletionClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(LlmAgent::class.java)

    fun generateSql(prompt: String): SqlQueryResult {
        val normalizedPrompt = prompt.trim()
        require(normalizedPrompt.isNotEmpty()) { "Prompt must not be blank." }
        logger.info("Prompt {}", normalizedPrompt)

        var rawResponse: String? = null
        try {
            val content = chatCompletionClient.complete(normalizedPrompt).trim()
            rawResponse = content
            logger.info("Raw LLM response. content='{}'", content)
            if (content.isEmpty()) {
                throw QueryExecutionException("LLM returned an empty response.")
            }

            val json = cleanJson(content)
            if (json != content) {
                logger.warn("Cleaned LLM JSON response before parsing. cleaned='{}'", json)
            }
            val result = objectMapper.readValue(json, SqlQueryResult::class.java)
            logger.info(
                "Generated SQL from LLM. sql='{}', explanation='{}', dialect='{}'",
                result.sql,
                result.explanation,
                result.dialect
            )
            return validateResult(result)
        } catch (exception: QueryExecutionException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(
                "Failed to parse LLM response. rawResponse='{}'",
                rawResponse,
                exception
            )
            throw QueryExecutionException("Failed to generate SQL from LLM response.", exception)
        }
    }

    private fun validateResult(result: SqlQueryResult): SqlQueryResult {
        require(result.dialect.isNotBlank()) { "LLM response must include a dialect." }
        require(result.explanation.isNotBlank()) { "LLM response must include an explanation." }
        return result.copy(
            sql = result.sql.trim(),
            explanation = result.explanation.trim(),
            dialect = result.dialect.trim()
        )
    }

    internal fun cleanJson(content: String): String {
        val trimmed = content.trim()
        val unfenced = if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }

        if (unfenced.startsWith("{") && unfenced.endsWith("}")) {
            return unfenced
        }

        return extractFirstJsonObject(unfenced)
            ?: closeTrailingObjectIfPossible(unfenced)
            ?: unfenced
    }

    private fun extractFirstJsonObject(content: String): String? {
        val start = content.indexOf('{')
        if (start == -1) {
            return null
        }

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until content.length) {
            val char = content[index]

            if (escaped) {
                escaped = false
                continue
            }

            if (char == '\\' && inString) {
                escaped = true
                continue
            }

            if (char == '"') {
                inString = !inString
                continue
            }

            if (inString) {
                continue
            }

            when (char) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return content.substring(start, index + 1)
                    }
                }
            }
        }

        return null
    }

    private fun closeTrailingObjectIfPossible(content: String): String? {
        val start = content.indexOf('{')
        if (start == -1) {
            return null
        }

        val candidate = content.substring(start).trim()
        if (!candidate.startsWith("{") || candidate.endsWith("}")) {
            return null
        }

        var depth = 0
        var inString = false
        var escaped = false

        candidate.forEach { char ->
            if (escaped) {
                escaped = false
                return@forEach
            }

            if (char == '\\' && inString) {
                escaped = true
                return@forEach
            }

            if (char == '"') {
                inString = !inString
                return@forEach
            }

            if (inString) {
                return@forEach
            }

            when (char) {
                '{' -> depth++
                '}' -> depth--
            }
        }

        return if (!inString && depth > 0) {
            candidate + "}".repeat(depth)
        } else {
            null
        }
    }
}

fun interface ChatCompletionClient {
    fun complete(prompt: String): String
}

@Component
class SpringAiChatCompletionClient(
    private val chatClient: ChatClient
) : ChatCompletionClient {

    override fun complete(prompt: String): String =
        chatClient
            .prompt(prompt)
            .call()
            .content()
            .orEmpty()
}
