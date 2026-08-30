package quiz.controller

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import quiz.domain.model.User
import quiz.domain.repository.UserRepository
import quiz.fakeservice.FakeIdGenerator

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PostRegisterUserIntegrationTest {

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
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var idGenerator: FakeIdGenerator

    private val existingEmail = "existing-user@example.com"
    private val existingPassword = "password123"

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail(existingEmail)) {
            userRepository.save(
                User(
                    id = idGenerator.createId(),
                    email = existingEmail,
                    password = passwordEncoder.encode(existingPassword) ?: error("Password encoding failed")
                )
            )
        }
    }

    private fun registerJson(email: String, password: String): String =
        buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()

    private fun post(body: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate.postForEntity("/api/register", entity, String::class.java)
    }

    private fun errorJson(status: Int, error: String, message: String): String =
        buildJsonObject {
            put("status", status)
            put("error", error)
            put("message", message)
        }.toString()

    @Nested
    inner class `given the registration is valid` {
        @Test
        fun `should register the user and return 200 with an empty body`() {
            val email = "new-user@example.com"
            val password = "password123"

            val response = post(registerJson(email, password))

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body.isNullOrEmpty())

            val savedUser = userRepository.findByEmail(email)
            assertNotNull(savedUser)
            assertEquals(email, savedUser.email)
            assertNotEquals(password, savedUser.password)
            assertTrue(passwordEncoder.matches(password, savedUser.password))
        }
    }

    @Nested
    inner class `given the email is already registered` {
        @Test
        fun `should reject the request`() {
            val response = post(registerJson(existingEmail, "anotherPassword"))

            assertEquals(HttpStatus.CONFLICT, response.statusCode)
            assertEquals(
                errorJson(409, "DUPLICATE_EMAIL", "Email already taken: $existingEmail"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the email is invalid` {
        @Test
        fun `should reject a malformed email`() {
            val response = post(registerJson("not-an-email", "password123"))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "email: invalid email address"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the password is invalid` {
        @Test
        fun `should reject a password shorter than 8 characters`() {
            val response = post(registerJson("short-password@example.com", "1234567"))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "password: Password must be at least 8 characters long"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the request body itself is malformed` {
        @Test
        fun `should reject malformed JSON with 400`() {
            val response = post("""{"email":"a@example.com","password":}""")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "MALFORMED_REQUEST", "Request body could not be parsed"),
                response.body ?: error("Response body was null")
            )
        }
    }
}
