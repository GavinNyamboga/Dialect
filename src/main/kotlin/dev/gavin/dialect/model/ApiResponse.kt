package dev.gavin.dialect.model

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val timestamp: Instant
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(
            success = true,
            data = data,
            timestamp = Instant.now()
        )
    }
}
