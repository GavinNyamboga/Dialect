package dev.gavin.dialect.agent

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

@Service
class PromptAgent(
    private val schemaRetrievalAgent: SchemaRetrievalAgent,
    @Value("classpath:prompts/sql-generation.st")
    private val promptTemplate: Resource
) {

    fun buildPrompt(question: String): String {
        val normalizedQuestion = question.trim()
        require(normalizedQuestion.isNotEmpty()) { "Question must not be blank." }

        return renderPrompt(
            template = loadTemplate(),
            question = normalizedQuestion,
            schema = schemaRetrievalAgent.retrieveSchema(normalizedQuestion)
        )
    }

    internal fun renderPrompt(
        template: String,
        question: String,
        schema: String
    ): String {
        val normalizedTemplate = template.trim()
        val normalizedQuestion = question.trim()
        val normalizedSchema = schema.trim()

        require(normalizedTemplate.isNotEmpty()) { "Prompt template must not be blank." }
        require(normalizedQuestion.isNotEmpty()) { "Question must not be blank." }
        require(normalizedSchema.isNotEmpty()) { "Schema must not be blank." }

        return normalizedTemplate
            .replace("<question>", normalizedQuestion)
            .replace("<schema>", normalizedSchema)
            .trim()
    }

    private fun loadTemplate(): String =
        promptTemplate.inputStream.use { inputStream ->
            inputStream.readBytes().toString(StandardCharsets.UTF_8)
        }
}
