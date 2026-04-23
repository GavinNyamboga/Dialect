package dev.gavin.dialect.agent

import org.springframework.stereotype.Service
import java.util.ArrayDeque

@Service
class SchemaRetrievalAgent(
    private val schemaAgent: SchemaAgent
) {

    /**
     * Loads the full schema catalog, selects the most relevant subset for the question,
     * and formats that subset into the prompt-friendly schema text consumed by the LLM.
     */
    fun retrieveSchema(question: String): String {
        val normalizedQuestion = question.trim()
        require(normalizedQuestion.isNotEmpty()) { "Question must not be blank." }

        val catalog = schemaAgent.loadCatalog()
        return formatCatalog(selectRelevantCatalog(catalog, normalizedQuestion))
    }

    /**
     * Narrows a full schema catalog to the tables most relevant to the question.
     *
     * The selection starts with keyword scoring against table and column names, then
     * expands through foreign-key relationships so the final subset still contains
     * enough join structure for SQL generation.
     */
    internal fun selectRelevantCatalog(
        catalog: SchemaCatalog,
        question: String
    ): SchemaCatalog {
        val normalizedQuestion = question.trim()
        require(normalizedQuestion.isNotEmpty()) { "Question must not be blank." }

        val tokens = tokenize(normalizedQuestion)
        if (tokens.isEmpty()) {
            return catalog
        }

        val tables = catalog.columns
            .groupBy { TableIdentifier(it.schemaName, it.tableName) }

        val rankedTables = tables.keys
            .map { table -> table to scoreTable(table, tables.getValue(table), tokens) }
            .sortedWith(compareByDescending<Pair<TableIdentifier, Int>> { it.second }.thenBy { it.first.schemaName }.thenBy { it.first.tableName })

        val seedTables = rankedTables
            .filter { it.second > 0 }
            .take(MAX_SEED_TABLES)
            .map { it.first }

        if (seedTables.isEmpty()) {
            return catalog
        }

        val selectedTables = expandTablesWithJoinPaths(seedTables, catalog.foreignKeys)
        val selectedColumns = catalog.columns.filter {
            TableIdentifier(it.schemaName, it.tableName) in selectedTables
        }
        val selectedRelationships = catalog.foreignKeys.filter {
            TableIdentifier(it.sourceSchema, it.sourceTable) in selectedTables &&
                TableIdentifier(it.targetSchema, it.targetTable) in selectedTables
        }

        return SchemaCatalog(
            columns = selectedColumns,
            foreignKeys = selectedRelationships
        )
    }

    /**
     * Formats a schema catalog into the compact textual representation injected into prompts.
     *
     * The output intentionally mirrors the full-schema formatting so prompt rules and tests
     * do not need a separate shape for retrieved subsets.
     */
    internal fun formatCatalog(catalog: SchemaCatalog): String {
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

    /**
     * Assigns a relevance score to a table by comparing normalized question tokens against
     * the table name and its column names.
     *
     * Table-name matches are weighted more heavily than column-name matches because they
     * usually signal stronger intent.
     */
    private fun scoreTable(
        table: TableIdentifier,
        columns: List<SchemaColumn>,
        tokens: Set<String>
    ): Int {
        val tableTokens = tokenize("${table.schemaName} ${table.tableName}")
        val columnTokens = columns.flatMapTo(linkedSetOf()) { tokenize(it.columnName) }

        return tokens.sumOf { token ->
            var score = 0
            if (token in tableTokens) {
                score += 6
            }
            if (tableTokens.any { it.contains(token) || token.contains(it) }) {
                score += 3
            }
            if (token in columnTokens) {
                score += 3
            }
            if (columnTokens.any { it.contains(token) || token.contains(it) }) {
                score += 1
            }
            score
        }
    }

    /**
     * Expands the initially selected seed tables with foreign-key neighbors and shortest
     * join paths between seeds so the retrieved subset remains connected.
     */
    private fun expandTablesWithJoinPaths(
        seedTables: List<TableIdentifier>,
        foreignKeys: List<ForeignKeyRelationship>
    ): Set<TableIdentifier> {
        val selected = seedTables.toMutableSet()
        val adjacency = buildAdjacencyMap(foreignKeys)

        seedTables.zipWithNext().forEach { (start, end) ->
            shortestPath(start, end, adjacency)?.let { selected.addAll(it) }
        }

        seedTables.forEach { seed ->
            adjacency[seed].orEmpty()
                .take(MAX_NEIGHBOR_TABLES_PER_SEED)
                .forEach { selected.add(it) }
        }

        return selected
    }

    /**
     * Builds an undirected adjacency map from foreign-key relationships so path-finding
     * can move across tables in either direction.
     */
    private fun buildAdjacencyMap(
        foreignKeys: List<ForeignKeyRelationship>
    ): Map<TableIdentifier, Set<TableIdentifier>> {
        val adjacency = mutableMapOf<TableIdentifier, MutableSet<TableIdentifier>>()

        foreignKeys.forEach { relationship ->
            val source = TableIdentifier(relationship.sourceSchema, relationship.sourceTable)
            val target = TableIdentifier(relationship.targetSchema, relationship.targetTable)
            adjacency.getOrPut(source) { linkedSetOf() }.add(target)
            adjacency.getOrPut(target) { linkedSetOf() }.add(source)
        }

        return adjacency
    }

    /**
     * Finds the shortest table-to-table path in the foreign-key graph using breadth-first search.
     *
     * The result is used only to preserve joinability in prompt context, not to produce
     * actual SQL joins directly.
     */
    private fun shortestPath(
        start: TableIdentifier,
        end: TableIdentifier,
        adjacency: Map<TableIdentifier, Set<TableIdentifier>>
    ): List<TableIdentifier>? {
        if (start == end) {
            return listOf(start)
        }

        val queue = ArrayDeque<TableIdentifier>()
        val previous = mutableMapOf<TableIdentifier, TableIdentifier?>()
        queue.add(start)
        previous[start] = null

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            adjacency[current].orEmpty().forEach { neighbor ->
                if (neighbor in previous) {
                    return@forEach
                }

                previous[neighbor] = current
                if (neighbor == end) {
                    val path = mutableListOf<TableIdentifier>()
                    var cursor: TableIdentifier? = end
                    while (cursor != null) {
                        path.add(cursor)
                        cursor = previous[cursor]
                    }
                    return path.asReversed()
                }
                queue.add(neighbor)
            }
        }

        return null
    }

    /**
     * Normalizes arbitrary text into a set of matching tokens used for schema ranking.
     *
     * Tokens are lowercased, split on non-word characters, expanded for underscores,
     * plural/singular variants, and a small set of generic business synonyms.
     */
    private fun tokenize(value: String): Set<String> =
        TOKEN_REGEX.findAll(value.lowercase())
            .map { it.value }
            .flatMap { token -> expandToken(token).asSequence() }
            .filter { it.length > 1 }
            .toSet()

    /**
     * Expands a single token into additional forms that improve table matching.
     *
     * This includes underscore-separated parts, inflections, and configured synonym hints.
     */
    private fun expandToken(token: String): Set<String> {
        val expanded = linkedSetOf(token)
        if ('_' in token) {
            token.split('_')
                .filter { it.length > 1 }
                .forEach { part ->
                    expanded += part
                    expanded += inflections(part)
                    expanded += TOKEN_SYNONYMS[part].orEmpty()
                }
        }
        expanded += inflections(token)
        expanded += TOKEN_SYNONYMS[token].orEmpty()
        return expanded
    }

    /**
     * Produces simple plural/singular variants for a token so common noun forms match
     * the schema without requiring exact wording from the user.
     */
    private fun inflections(token: String): Set<String> {
        val expanded = linkedSetOf(token)
        if (token.endsWith("ies") && token.length > 3) {
            expanded += token.dropLast(3) + "y"
        }
        if (token.endsWith("es") && token.length > 3) {
            expanded += token.dropLast(2)
        }
        if (token.endsWith("s") && token.length > 2) {
            expanded += token.dropLast(1)
        }
        return expanded
    }

    private companion object {
        private const val MAX_SEED_TABLES = 5
        private const val MAX_NEIGHBOR_TABLES_PER_SEED = 2
        private val TOKEN_REGEX = Regex("[a-z0-9_]+")
        private val TOKEN_SYNONYMS = mapOf(
            "customer" to setOf("client", "account", "user", "person"),
            "customers" to setOf("client", "clients", "account", "accounts", "user", "users", "person", "people"),
            "client" to setOf("customer", "account", "user"),
            "clients" to setOf("customers", "accounts", "users"),
            "account" to setOf("customer", "client", "user"),
            "accounts" to setOf("customers", "clients", "users"),
            "user" to setOf("customer", "client", "account", "person"),
            "users" to setOf("customers", "clients", "accounts", "people"),
            "person" to setOf("user", "customer", "client"),
            "people" to setOf("users", "customers", "clients"),
            "revenue" to setOf("sales", "amount", "total", "price"),
            "sales" to setOf("revenue", "amount", "total"),
            "product" to setOf("item", "sku"),
            "products" to setOf("items", "skus"),
            "order" to setOf("purchase", "transaction"),
            "orders" to setOf("purchases", "transactions")
        )
    }
}
