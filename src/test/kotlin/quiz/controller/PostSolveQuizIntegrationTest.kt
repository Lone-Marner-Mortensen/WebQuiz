package quiz.controller

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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
import kotlinx.serialization.json.add
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
import quiz.repository.jpa.adapter.QuizCompletionEntityRepository
import quiz.repository.jpa.adapter.QuizEntityRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PostSolveQuizIntegrationTest {

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

    @Autowired
    private lateinit var quizCompletionEntityRepository: QuizCompletionEntityRepository

    private val email = "quiz-solver@example.com"
    private val password = "password123"

    @AfterEach
    fun tearDown() {
        quizCompletionEntityRepository.deleteAll()
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

    private fun solveJson(answers: List<Int>): String =
        buildJsonObject {
            putJsonArray("answers") { answers.forEach { add(it) } }
        }.toString()

    private fun solve(quizId: String, body: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate
            .withBasicAuth(email, password)
            .postForEntity("/api/quizzes/$quizId/solve", entity, String::class.java)
    }

    private fun solveWithoutLogin(quizId: String, body: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate.postForEntity("/api/quizzes/$quizId/solve", entity, String::class.java)
    }

    private fun errorJson(status: Int, error: String, message: String): String =
        buildJsonObject {
            put("status", status)
            put("error", error)
            put("message", message)
        }.toString()

    private fun answerResultJson(success: Boolean, feedback: String): String =
        buildJsonObject {
            put("success", success)
            put("feedback", feedback)
        }.toString()

    @Nested
    inner class `given the answers are correct` {
        @Test
        fun `should return success and record a completion`() {
            val authorId = ensureAuthorExists()
            val quiz = saveQuiz(authorId)

            val response = solve(quiz.id, solveJson(listOf(0)))

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(
                answerResultJson(true, "Congratulations, you're right!"),
                response.body ?: error("Response body was null")
            )
            assertEquals(true, quizCompletionEntityRepository.existsByQuizIdAndUserId(quiz.id, authorId))
        }

        @Test
        fun `should return success without recording a duplicate completion when already solved`() {
            val authorId = ensureAuthorExists()
            val quiz = saveQuiz(authorId)
            solve(quiz.id, solveJson(listOf(0)))

            val response = solve(quiz.id, solveJson(listOf(0)))

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(
                answerResultJson(true, "Congratulations, you're right!"),
                response.body ?: error("Response body was null")
            )
            assertEquals(1L, quizCompletionEntityRepository.count())
        }
    }

    @Nested
    inner class `given an answer is wrong` {
        @Test
        fun `should return failure and not record a completion`() {
            val authorId = ensureAuthorExists()
            val quiz = saveQuiz(authorId)

            val response = solve(quiz.id, solveJson(listOf(1)))

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(
                answerResultJson(false, "Wrong answer(s)! Please, try again."),
                response.body ?: error("Response body was null")
            )
            assertEquals(false, quizCompletionEntityRepository.existsByQuizIdAndUserId(quiz.id, authorId))
        }
    }

    @Nested
    inner class `given the number of answers does not match the number of questions` {
        @Test
        fun `should reject the request`() {
            val authorId = ensureAuthorExists()
            val quiz = saveQuiz(authorId)

            val response = solve(quiz.id, solveJson(emptyList()))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "INVALID_ANSWER", "Expected 1 answer(s), got 0"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the quiz does not exist` {
        @Test
        fun `should return 404`() {
            ensureAuthorExists()

            val response = solve("does-not-exist", solveJson(listOf(0)))

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
            val response = solveWithoutLogin("does-not-exist", solveJson(listOf(0)))

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }
}
