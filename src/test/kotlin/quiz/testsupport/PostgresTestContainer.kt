package quiz.testsupport

import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestContainer {
    val instance: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
        .withDatabaseName("webquiz")
        .withUsername("webquiz")
        .withPassword("webquiz")
        .apply { start() }
}
