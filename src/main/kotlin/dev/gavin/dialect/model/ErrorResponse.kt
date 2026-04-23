package dev.gavin.dialect.model

import java.time.Instant

data class ErrorResponse(
    val success: Boolean,
    val error: ApiError,
    val timestamp: Instant
) {
    companion object {
        fun failure(error: ApiError): ErrorResponse = ErrorResponse(
            success = false,
            error = error,
            timestamp = Instant.now()
        )
    }
}
