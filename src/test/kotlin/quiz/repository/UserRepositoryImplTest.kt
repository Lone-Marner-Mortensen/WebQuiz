package quiz.repository

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import quiz.domain.model.User
import quiz.domain.repository.UserRepository
import quiz.repository.jpa.adapter.UserEntityRepository

@SpringBootTest
class UserRepositoryImplTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("webquiz")
            .withUsername("webquiz")
            .withPassword("webquiz")
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }

        @JvmStatic
        @AfterAll
        fun tearDownContainer() {
            postgres.stop()
        }
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userEntityRepository: UserEntityRepository

    @AfterEach
    fun tearDown() {
        userEntityRepository.deleteAll()
    }

    private fun user(id: String, email: String): User =
        User(id = id, email = email, password = "encoded-password")

    @Nested
    inner class Save {
        @Test
        fun `persists a user and returns it as a domain object`() {
            val newUser = user(id = "user-1", email = "user@example.com")

            val result = userRepository.save(newUser)

            assertEquals(newUser, result)
            assertEquals(true, userEntityRepository.existsById("user-1"))
        }
    }

    @Nested
    inner class FindByEmail {
        @Test
        fun `returns null when no user has that email`() {
            assertNull(userRepository.findByEmail("missing@example.com"))
        }

        @Test
        fun `returns the user when it exists`() {
            val savedUser = userRepository.save(user(id = "user-1", email = "user@example.com"))

            val result = userRepository.findByEmail("user@example.com")

            assertEquals(savedUser, result)
        }
    }

    @Nested
    inner class ExistsByEmail {
        @Test
        fun `returns false when no user has that email`() {
            assertEquals(false, userRepository.existsByEmail("missing@example.com"))
        }

        @Test
        fun `returns true when a user has that email`() {
            userRepository.save(user(id = "user-1", email = "user@example.com"))

            assertEquals(true, userRepository.existsByEmail("user@example.com"))
        }
    }
}
