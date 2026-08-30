package quiz.controller

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.model.User
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import quiz.fakeservice.FakeIdGenerator
import quiz.repository.jpa.adapter.QuizEntityRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PostDeleteQuizIntegrationTest {

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
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var idGenerator: FakeIdGenerator

    @Autowired
    private lateinit var quizEntityRepository: QuizEntityRepository

    private val authorEmail = "quiz-author@example.com"
    private val otherUserEmail = "not-the-author@example.com"
    private val password = "password123"

    @AfterEach
    fun tearDown() {
        quizEntityRepository.deleteAll()
    }

    private fun ensureUserExists(id: String, email: String): String =
        userRepository.findByEmail(email)?.id ?: run {
            val user = User(
                id = id,
                email = email,
                password = passwordEncoder.encode(password) ?: error("Password encoding failed")
            )
            userRepository.save(user)
            user.id
        }

    private fun saveQuiz(authorId: String): Quiz =
        quizRepository.save(
            Quiz(
                id = idGenerator.id,
                title = "Geography",
                authorId = authorId,
                questions = listOf(Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0)),
                createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            )
        )

    private fun deleteQuiz(id: String, email: String): ResponseEntity<String> =
        restTemplate.withBasicAuth(email, password).exchange("/api/quizzes/$id", HttpMethod.DELETE, null, String::class.java)

    private fun deleteQuizWithoutLogin(id: String): ResponseEntity<String> =
        restTemplate.exchange("/api/quizzes/$id", HttpMethod.DELETE, null, String::class.java)

    private fun errorJson(status: Int, error: String, message: String): String =
        buildJsonObject {
            put("status", status)
            put("error", error)
            put("message", message)
        }.toString()

    @Nested
    inner class `given the requester is the quiz author` {
        @Test
        fun `should delete the quiz and return 204`() {
            val authorId = ensureUserExists("author-id", authorEmail)
            val quiz = saveQuiz(authorId)

            val response = deleteQuiz(quiz.id, authorEmail)

            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
            assertEquals(false, quizEntityRepository.existsById(quiz.id))
        }
    }

    @Nested
    inner class `given the requester is not the quiz author` {
        @Test
        fun `should return 403 and not delete the quiz`() {
            val authorId = ensureUserExists("author-id", authorEmail)
            ensureUserExists("other-user-id", otherUserEmail)
            val quiz = saveQuiz(authorId)

            val response = deleteQuiz(quiz.id, otherUserEmail)

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            assertEquals(
                errorJson(403, "QUIZ_AUTHOR_MISMATCH", "Requester is not the quiz author"),
                response.body ?: error("Response body was null")
            )
            assertEquals(true, quizEntityRepository.existsById(quiz.id))
        }
    }

    @Nested
    inner class `given the quiz does not exist` {
        @Test
        fun `should return 404`() {
            ensureUserExists("author-id", authorEmail)

            val response = deleteQuiz("does-not-exist", authorEmail)

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            assertEquals(
                errorJson(404, "QUIZ_NOT_FOUND", "No quiz with id: does-not-exist"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the caller is not authenticated` {
        @Test
        fun `should reject the request`() {
            val response = deleteQuizWithoutLogin("does-not-exist")

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }
}
