package dev.gavin.dialect.model

data class ApiError(
    val code: String,
    val message: String,
    val status: Int,
    val path: String,
    val details: Map<String, Any?> = emptyMap()
)
