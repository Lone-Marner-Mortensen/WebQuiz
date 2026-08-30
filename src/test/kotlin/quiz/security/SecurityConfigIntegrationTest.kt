package quiz.security

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestClassOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import quiz.domain.IdGenerator
import quiz.domain.model.User
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import quiz.repository.jpa.adapter.UserEntityRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestClassOrder(ClassOrderer.OrderAnnotation::class)
class SecurityConfigIntegrationTest {

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
    private lateinit var idGenerator: IdGenerator

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var userEntityRepository: UserEntityRepository

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail("matrix-user@example.com")) {
            userRepository.save(
                User(
                    id = idGenerator.createId(),
                    email = "matrix-user@example.com",
                    password = passwordEncoder.encode("password123") ?: error("Password encoding failed")
                )
            )
        }
    }

    @Nested
    @Order(1)
    inner class `requesting with authentication` {
        @Test
        fun `POST register is accepted`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val email = "matrix-register-${UUID.randomUUID()}@example.com"
            val entity = HttpEntity("{\"email\":\"$email\",\"password\":\"password123\"}", headers)
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .postForEntity("/api/register", entity, String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode)
        }

        @Test
        fun `GET quizzes list is accepted`() {
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .getForEntity("/api/quizzes", String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode)
        }

        @Test
        fun `POST create quiz is accepted`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity("{\"title\":\"T\",\"questions\":[{\"text\":\"Q\",\"options\":[\"a\",\"b\"],\"answer\":0}]}", headers)
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .postForEntity("/api/quizzes", entity, String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode)
        }

        @Test
        fun `POST solve quiz is accepted`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity("{\"answers\":[]}", headers)
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .postForEntity("/api/quizzes/does-not-exist/solve", entity, String::class.java)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `GET single quiz by id is accepted`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .getForEntity("/api/quizzes/does-not-exist", String::class.java)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `DELETE quiz is accepted`() {
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .exchange("/api/quizzes/does-not-exist", HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java)
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `GET completed quizzes is accepted`() {
            val response = restTemplate
                .withBasicAuth("matrix-user@example.com", "password123")
                .getForEntity("/api/quizzes/completed", String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    @Nested
    @Order(2)
    inner class `requesting without authentication` {
        @Test
        fun `GET quizzes list is rejected`() {
            val response = restTemplate.getForEntity("/api/quizzes", String::class.java)
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }

        @Test
        fun `GET single quiz by id is rejected`() {
            val response = restTemplate.getForEntity("/api/quizzes/does-not-exist", String::class.java)
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }

        @Test
        fun `POST solve quiz is rejected`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity("{\"answers\":[]}", headers)
            val response = restTemplate.postForEntity("/api/quizzes/does-not-exist/solve", entity, String::class.java)
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }

        @Test
        fun `POST create quiz is rejected`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity("{}", headers)
            val response = restTemplate.postForEntity("/api/quizzes", entity, String::class.java)
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }

        @Test
        fun `GET completed quizzes is rejected`() {
            val response = restTemplate.getForEntity("/api/quizzes/completed", String::class.java)
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }

        @Test
        fun `POST register is accepted`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val email = "matrix-register-${UUID.randomUUID()}@example.com"
            val entity = HttpEntity("{\"email\":\"$email\",\"password\":\"password123\"}", headers)
            val response = restTemplate
                .postForEntity("/api/register", entity, String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode)
        }

        @Test
        fun `DELETE quiz is rejected`() {
            val response = restTemplate
                .exchange("/api/quizzes/does-not-exist", HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java)
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }
}
