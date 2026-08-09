package quiz.testsupport

import org.testcontainers.containers.PostgreSQLContainer

//
//
// NOT READY FOR REVIEW
//
//
//

object PostgresTestContainer {
    val instance: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
        .withDatabaseName("webquiz")
        .withUsername("webquiz")
        .withPassword("webquiz")
        .apply { start() }
}
