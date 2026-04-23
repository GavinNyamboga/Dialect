package dev.gavin.dialect.exception

import dev.gavin.dialect.model.ApiError
import dev.gavin.dialect.model.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        exception: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = buildErrorResponse(
        status = HttpStatus.BAD_REQUEST,
        code = "INVALID_REQUEST",
        message = exception.message ?: "The request is invalid.",
        request = request
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = buildErrorResponse(
        status = HttpStatus.BAD_REQUEST,
        code = "MALFORMED_JSON",
        message = "Request body could not be parsed.",
        request = request
    )

    @ExceptionHandler(SqlValidationException::class)
    fun handleSqlValidation(
        exception: SqlValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = buildErrorResponse(
        status = HttpStatus.UNPROCESSABLE_ENTITY,
        code = "SQL_VALIDATION_FAILED",
        message = exception.message ?: "Generated SQL failed validation.",
        request = request
    )

    @ExceptionHandler(QueryExecutionException::class)
    fun handleQueryExecution(
        exception: QueryExecutionException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = buildErrorResponse(
        status = HttpStatus.BAD_GATEWAY,
        code = "QUERY_EXECUTION_FAILED",
        message = exception.message ?: "Failed to execute query.",
        request = request
    )

    @ExceptionHandler(SchemaLoadException::class)
    fun handleSchemaLoad(
        exception: SchemaLoadException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = buildErrorResponse(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        code = "SCHEMA_LOAD_FAILED",
        message = exception.message ?: "Failed to load schema metadata.",
        request = request
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> = buildErrorResponse(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        code = "INTERNAL_SERVER_ERROR",
        message = "An unexpected error occurred.",
        request = request
    )

    private fun buildErrorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse.failure(
            error = ApiError(
                code = code,
                message = message,
                status = status.value(),
                path = request.requestURI
            )
        )

        return ResponseEntity.status(status).body(body)
    }
}
