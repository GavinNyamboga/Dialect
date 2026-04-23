package dev.gavin.dialect.config

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.ApplicationArguments
import org.springframework.core.io.ByteArrayResource
import org.springframework.jdbc.core.JdbcOperations

class SampleDataInitializerTests {

    @Test
    fun `runs sample data script when sample tables are missing`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        val sqlScriptRunner = Mockito.mock(SqlScriptRunner::class.java)
        val script = ByteArrayResource("SELECT 1;".toByteArray())

        Mockito.`when`(jdbcOperations.queryForObject(Mockito.anyString(), Mockito.eq(Int::class.javaObjectType)))
            .thenReturn(0)

        SampleDataInitializer(jdbcOperations, sqlScriptRunner, script)
            .run(Mockito.mock(ApplicationArguments::class.java))

        Mockito.verify(sqlScriptRunner).run(script)
    }

    @Test
    fun `skips sample data script when all sample tables exist`() {
        val jdbcOperations = Mockito.mock(JdbcOperations::class.java)
        val sqlScriptRunner = Mockito.mock(SqlScriptRunner::class.java)
        val script = ByteArrayResource("SELECT 1;".toByteArray())

        Mockito.`when`(jdbcOperations.queryForObject(Mockito.anyString(), Mockito.eq(Int::class.javaObjectType)))
            .thenReturn(4)

        SampleDataInitializer(jdbcOperations, sqlScriptRunner, script)
            .run(Mockito.mock(ApplicationArguments::class.java))

        Mockito.verifyNoInteractions(sqlScriptRunner)
    }
}
