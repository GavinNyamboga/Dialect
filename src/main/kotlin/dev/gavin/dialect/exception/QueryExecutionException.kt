package dev.gavin.dialect.exception

class QueryExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)