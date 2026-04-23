package dev.gavin.dialect.agent

import dev.gavin.dialect.exception.SchemaLoadException
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class SchemaAgent(
    private val jdbcOperations: JdbcOperations
) {
    private val clock: Clock = Clock.systemUTC()
    private val cacheTtl: Duration = Duration.ofMinutes(5)

    @Volatile
    private var cachedSnapshot: CachedSchemaSnapshot? = null

    fun loadSchema(): String = loadSchema(forceRefresh = false)

    fun loadCatalog(): SchemaCatalog = loadCatalog(forceRefresh = false)

    fun loadSchema(forceRefresh: Boolean): String {
        return formatSchema(loadCatalog(forceRefresh))
    }

    fun loadCatalog(forceRefresh: Boolean): SchemaCatalog {
        val now = clock.instant()
        val cached = cachedSnapshot

        if (!forceRefresh && cached != null && !cached.isExpired(now)) {
            return cached.catalog
        }

        return synchronized(this) {
            val freshNow = clock.instant()
            val refreshedCache = cachedSnapshot
            if (!forceRefresh && refreshedCache != null && !refreshedCache.isExpired(freshNow)) {
                refreshedCache.catalog
            } else {
                val catalog = fetchCatalog()
                cachedSnapshot = CachedSchemaSnapshot(
                    catalog = catalog,
                    expiresAt = freshNow.plus(cacheTtl)
                )
                catalog
            }
        }
    }

    fun clearCache() {
        cachedSnapshot = null
    }

    private fun fetchCatalog(): SchemaCatalog {
        try {
            val rows = jdbcOperations.queryForList(SCHEMA_QUERY)
                .map(::toSchemaColumn)

            if (rows.isEmpty()) {
                throw SchemaLoadException("No application tables were found in the database schema.")
            }

            val foreignKeys = jdbcOperations.queryForList(FOREIGN_KEY_QUERY)
                .map(::toForeignKey)

            return SchemaCatalog(rows, foreignKeys)
        } catch (exception: SchemaLoadException) {
            throw exception
        } catch (exception: Exception) {
            throw SchemaLoadException("Failed to load database schema.", exception)
        }
    }

    private fun toSchemaColumn(row: Map<String, Any?>): SchemaColumn = SchemaColumn(
        schemaName = row.requiredString("table_schema"),
        tableName = row.requiredString("table_name"),
        columnName = row.requiredString("column_name"),
        dataType = row.requiredString("data_type"),
        nullable = row.requiredString("is_nullable").equals("YES", ignoreCase = true),
        defaultValue = row["column_default"]?.toString()
    )

    private fun toForeignKey(row: Map<String, Any?>): ForeignKeyRelationship = ForeignKeyRelationship(
        sourceSchema = row.requiredString("source_schema"),
        sourceTable = row.requiredString("source_table"),
        sourceColumn = row.requiredString("source_column"),
        targetSchema = row.requiredString("target_schema"),
        targetTable = row.requiredString("target_table"),
        targetColumn = row.requiredString("target_column")
    )

    private fun formatSchema(catalog: SchemaCatalog): String {
        val builder = StringBuilder()
        builder.appendLine("Database dialect: PostgreSQL")
        builder.appendLine("Available tables:")

        catalog.columns
            .groupBy { it.schemaName to it.tableName }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .forEach { (identifier, tableColumns) ->
                builder.appendLine("- ${identifier.first}.${identifier.second}")
                tableColumns.forEach { column ->
                    builder.append("  - ${column.columnName}: ${column.dataType}")
                    builder.append(if (column.nullable) " nullable" else " not null")
                    column.defaultValue?.takeIf { it.isNotBlank() }?.let { defaultValue ->
                        builder.append(" default=$defaultValue")
                    }
                    builder.appendLine()
                }
            }

        builder.appendLine("Relationships:")
        if (catalog.foreignKeys.isEmpty()) {
            builder.appendLine("- No foreign key relationships were found.")
        } else {
            catalog.foreignKeys
                .sortedWith(
                    compareBy(
                        ForeignKeyRelationship::sourceSchema,
                        ForeignKeyRelationship::sourceTable,
                        ForeignKeyRelationship::sourceColumn,
                        ForeignKeyRelationship::targetSchema,
                        ForeignKeyRelationship::targetTable,
                        ForeignKeyRelationship::targetColumn
                    )
                )
                .forEach { foreignKey ->
                    builder.appendLine(
                        "- ${foreignKey.sourceSchema}.${foreignKey.sourceTable}.${foreignKey.sourceColumn} -> " +
                            "${foreignKey.targetSchema}.${foreignKey.targetTable}.${foreignKey.targetColumn}"
                    )
                }
        }

        return builder.toString().trim()
    }

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw SchemaLoadException("Schema metadata is missing required field '$key'.")

    private data class CachedSchemaSnapshot(
        val catalog: SchemaCatalog,
        val expiresAt: Instant
    ) {
        fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)
    }

    private companion object {
        private const val SCHEMA_QUERY = """
            SELECT
                table_schema,
                table_name,
                column_name,
                data_type,
                is_nullable,
                column_default
            FROM information_schema.columns
            WHERE table_schema NOT IN ('information_schema', 'pg_catalog')
            ORDER BY table_schema, table_name, ordinal_position
        """

        private const val FOREIGN_KEY_QUERY = """
            SELECT
                kcu.table_schema AS source_schema,
                kcu.table_name AS source_table,
                kcu.column_name AS source_column,
                ccu.table_schema AS target_schema,
                ccu.table_name AS target_table,
                ccu.column_name AS target_column
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_schema = kcu.constraint_schema
             AND tc.constraint_name = kcu.constraint_name
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_schema = tc.constraint_schema
             AND ccu.constraint_name = tc.constraint_name
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND kcu.table_schema NOT IN ('information_schema', 'pg_catalog')
              AND ccu.table_schema NOT IN ('information_schema', 'pg_catalog')
            ORDER BY kcu.table_schema, kcu.table_name, kcu.column_name
        """
    }
}

data class SchemaCatalog(
    val columns: List<SchemaColumn>,
    val foreignKeys: List<ForeignKeyRelationship>
) {
    fun columnsByTable(): Map<TableIdentifier, Set<String>> =
        columns
            .groupBy { TableIdentifier(it.schemaName, it.tableName) }
            .mapValues { (_, columns) -> columns.map { it.columnName.lowercase() }.toSet() }
}

data class TableIdentifier(
    val schemaName: String,
    val tableName: String
)

data class SchemaColumn(
    val schemaName: String,
    val tableName: String,
    val columnName: String,
    val dataType: String,
    val nullable: Boolean,
    val defaultValue: String?
)

data class ForeignKeyRelationship(
    val sourceSchema: String,
    val sourceTable: String,
    val sourceColumn: String,
    val targetSchema: String,
    val targetTable: String,
    val targetColumn: String
)
