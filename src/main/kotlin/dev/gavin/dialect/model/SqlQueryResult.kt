package dev.gavin.dialect.model

data class SqlQueryResult(
    val sql: String,
    val explanation: String,
    val dialect: String
)