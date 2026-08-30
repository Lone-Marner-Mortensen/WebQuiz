package quiz.controller

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GetASingleQuizIntegrationTest {

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

    private val email = "single-quiz-reader@example.com"
    private val password = "password123"
    private val createdAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    @AfterEach
    fun tearDown() {
        quizEntityRepository.deleteAll()
    }

    private fun ensureAuthorExists(): String =
        userRepository.findByEmail(email)?.id ?: run {
            val user = User(
                id = idGenerator.createId(),
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

    private fun getQuiz(id: String): ResponseEntity<String> =
        restTemplate.withBasicAuth(email, password).getForEntity("/api/quizzes/$id", String::class.java)

    private fun getQuizWithoutLogin(id: String): ResponseEntity<String> =
        restTemplate.getForEntity("/api/quizzes/$id", String::class.java)

    private fun errorJson(status: Int, error: String, message: String): String =
        buildJsonObject {
            put("status", status)
            put("error", error)
            put("message", message)
        }.toString()

    private fun quizResponseJson(id: String, title: String, questions: List<Question>, createdAt: OffsetDateTime): String =
        buildJsonObject {
            put("id", id)
            put("title", title)
            putJsonArray("questions") {
                questions.forEach { question ->
                    addJsonObject {
                        put("text", question.text)
                        putJsonArray("options") { question.options.forEach { add(it) } }
                        put("answer", question.answer)
                    }
                }
            }
            put("createdAt", createdAt.format(createdAtFormatter))
        }.toString()

    @Nested
    inner class `given the quiz exists` {
        @Test
        fun `should return the quiz`() {
            val authorId = ensureAuthorExists()
            val quiz = saveQuiz(authorId)

            val response = getQuiz(quiz.id)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(
                quizResponseJson(quiz.id, quiz.title, quiz.questions, quiz.createdAt),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the quiz does not exist` {
        @Test
        fun `should return 404`() {
            ensureAuthorExists()

            val response = getQuiz("does-not-exist")

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
            val response = getQuizWithoutLogin("does-not-exist")

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }
}
