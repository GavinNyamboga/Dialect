package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.SqlValidationException
import dev.gavin.dialect.model.SqlQueryResult
import org.springframework.stereotype.Service

@Service
class ValidationAgent(
    private val schemaAgent: SchemaAgent? = null
) {

    fun validate(result: SqlQueryResult): SqlQueryResult {
        validateDialect(result.dialect)
        return result.copy(sql = validateSql(result.sql))
    }

    fun validateSql(sql: String): String {
        val normalizedSql = sql.trim()

        if (normalizedSql.isEmpty()) {
            throw SqlValidationException("SQL must not be blank.")
        }

        val withoutComments = removeComments(normalizedSql).trim()
        val canonicalSql = withoutComments.trimEnd(';').trim()

        if (canonicalSql.isEmpty()) {
            throw SqlValidationException("SQL must not be blank.")
        }

        if (containsMultipleStatements(withoutComments)) {
            throw SqlValidationException("Only a single SQL statement is allowed.")
        }

        if (!canonicalSql.startsWith("select", ignoreCase = true)) {
            throw SqlValidationException("Only SELECT statements are allowed.")
        }

        if (FORBIDDEN_KEYWORDS.any { it.containsMatchIn(canonicalSql) }) {
            throw SqlValidationException("SQL contains a forbidden operation.")
        }

        schemaAgent?.let { validateAliasColumns(canonicalSql, it.loadCatalog()) }

        return canonicalSql
    }

    private fun validateDialect(dialect: String) {
        if (!dialect.equals("postgresql", ignoreCase = true)) {
            throw SqlValidationException("Only PostgreSQL SQL is supported.")
        }
    }

    private fun removeComments(sql: String): String =
        BLOCK_COMMENT_REGEX.replace(LINE_COMMENT_REGEX.replace(sql, " "), " ")

    private fun containsMultipleStatements(sql: String): Boolean {
        val semicolonCount = sql.count { it == ';' }
        return semicolonCount > 1 || (semicolonCount == 1 && !sql.trimEnd().endsWith(";"))
    }

    private fun validateAliasColumns(sql: String, catalog: SchemaCatalog) {
        val columnsByTable = catalog.columnsByTable()
        val aliases = extractAliases(sql, columnsByTable.keys)

        ALIAS_COLUMN_REGEX.findAll(sql).forEach { match ->
            val alias = normalizeIdentifier(match.groupValues[1])
            val column = normalizeIdentifier(match.groupValues[2]).lowercase()
            val table = aliases[alias] ?: return@forEach
            val knownColumns = columnsByTable[table] ?: return@forEach

            if (column !in knownColumns) {
                throw SqlValidationException(
                    "Column '$column' does not exist on table '${table.schemaName}.${table.tableName}' aliased as '$alias'."
                )
            }
        }
    }

    private fun extractAliases(
        sql: String,
        knownTables: Set<TableIdentifier>
    ): Map<String, TableIdentifier> {
        val tablesByQualifiedName = knownTables.associateBy {
            "${it.schemaName.lowercase()}.${it.tableName.lowercase()}"
        }
        val tablesByName = knownTables.groupBy { it.tableName.lowercase() }
        val aliases = mutableMapOf<String, TableIdentifier>()

        TABLE_ALIAS_REGEX.findAll(sql).forEach { match ->
            val tableReference = normalizeTableReference(match.groupValues[1])
            val table = tablesByQualifiedName[tableReference]
                ?: tablesByName[tableReference]?.singleOrNull()
                ?: return@forEach

            val explicitAlias = match.groupValues[2].takeIf { it.isNotBlank() }
                ?.let(::normalizeIdentifier)
                ?.takeUnless { it in SQL_KEYWORDS }
            val alias = explicitAlias ?: table.tableName.lowercase()

            aliases[alias] = table
        }

        return aliases
    }

    private fun normalizeTableReference(value: String): String =
        value
            .split(".")
            .joinToString(".") { normalizeIdentifier(it) }
            .lowercase()

    private fun normalizeIdentifier(value: String): String =
        value.trim().trim('"').lowercase()

    private companion object {
        private val LINE_COMMENT_REGEX = Regex("--.*?(?=\\r?\\n|$)")
        private val BLOCK_COMMENT_REGEX = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        private val TABLE_ALIAS_REGEX = Regex(
            """(?i)\b(?:from|join)\s+((?:"?[a-z_][\w]*"?\.)?"?[a-z_][\w]*"?)(?:\s+(?:as\s+)?("?[a-z_][\w]*"?))?"""
        )
        private val ALIAS_COLUMN_REGEX = Regex("""\b("?[a-z_][\w]*"?)\.("?[a-z_][\w]*"?)\b""", RegexOption.IGNORE_CASE)
        private val SQL_KEYWORDS = setOf(
            "where",
            "join",
            "left",
            "right",
            "inner",
            "outer",
            "full",
            "cross",
            "on",
            "group",
            "order",
            "limit",
            "offset",
            "having",
            "union"
        )
        private val FORBIDDEN_KEYWORDS = listOf(
            "\\binsert\\b",
            "\\bupdate\\b",
            "\\bdelete\\b",
            "\\bmerge\\b",
            "\\bdrop\\b",
            "\\btruncate\\b",
            "\\balter\\b",
            "\\bcreate\\b",
            "\\breplace\\b",
            "\\bgrant\\b",
            "\\brevoke\\b",
            "\\bcall\\b",
            "\\bexecute\\b",
            "\\bvacuum\\b",
            "\\banalyze\\b",
            "\\bcopy\\b"
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
    }
}
