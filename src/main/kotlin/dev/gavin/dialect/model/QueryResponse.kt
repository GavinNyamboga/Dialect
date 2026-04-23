package dev.gavin.dialect.model

data class QueryResponse(
    val question: String,
    val sql: String,
    val explanation: String,
    val dialect: String,
    val rows: List<Map<String, Any?>>,
    val rowCount: Int,
    val executionTimeMs: Long
)
