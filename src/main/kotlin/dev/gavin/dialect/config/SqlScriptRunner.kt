package dev.gavin.dialect.config

import org.springframework.core.io.Resource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component
import javax.sql.DataSource

fun interface SqlScriptRunner {
    fun run(script: Resource)
}

@Component
class ResourceDatabaseSqlScriptRunner(
    private val dataSource: DataSource
) : SqlScriptRunner {

    override fun run(script: Resource) {
        ResourceDatabasePopulator(script).execute(dataSource)
    }
}
