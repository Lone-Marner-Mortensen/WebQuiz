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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import quiz.domain.model.User
import quiz.domain.repository.UserRepository
import quiz.fakeservice.FakeIdGenerator
import quiz.repository.entity.QuizCompletionEntity
import quiz.repository.entity.QuizEntity
import quiz.repository.jpa.adapter.QuizCompletionEntityRepository
import quiz.repository.jpa.adapter.QuizEntityRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GetCompletedQuizzesIntegrationTest {

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

    @Autowired
    private lateinit var quizEntityRepository: QuizEntityRepository

    @Autowired
    private lateinit var quizCompletionEntityRepository: QuizCompletionEntityRepository

    private val email = "quiz-completer@example.com"
    private val password = "password123"
    private val completedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    @AfterEach
    fun tearDown() {
        quizCompletionEntityRepository.deleteAll()
        quizEntityRepository.deleteAll()
    }

    private fun ensureUserExists(): String =
        userRepository.findByEmail(email)?.id ?: run {
            val user = User(
                id = idGenerator.createId(),
                email = email,
                password = passwordEncoder.encode(password) ?: error("Password encoding failed")
            )
            userRepository.save(user)
            user.id
        }

    private fun saveQuizEntity(id: String, authorId: String): QuizEntity =
        quizEntityRepository.save(
            QuizEntity(
                id = id,
                title = "Quiz $id",
                authorId = authorId,
                createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            )
        )

    private fun saveCompletionEntity(id: String, quizId: String, userId: String, completedAt: OffsetDateTime): QuizCompletionEntity =
        quizCompletionEntityRepository.save(
            QuizCompletionEntity(
                id = id,
                quizId = quizId,
                userId = userId,
                completedAt = completedAt
            )
        )

    private fun getCompletedQuizzes(page: Int? = null): ResponseEntity<String> {
        val path = if (page != null) "/api/quizzes/completed?page=$page" else "/api/quizzes/completed"
        return restTemplate.withBasicAuth(email, password).getForEntity(path, String::class.java)
    }

    private fun getCompletedQuizzesWithoutLogin(): ResponseEntity<String> =
        restTemplate.getForEntity("/api/quizzes/completed", String::class.java)

    private fun bodyOf(response: ResponseEntity<String>) =
        Json.parseToJsonElement(response.body ?: error("Response body was null")).jsonObject

    private fun contentOf(response: ResponseEntity<String>) =
        bodyOf(response)["content"]?.jsonArray ?: error("content field missing from response: ${response.body}")

    private fun completionResponseJson(quizId: String, completedAt: OffsetDateTime): String =
        buildJsonObject {
            put("id", quizId)
            put("completedAt", completedAt.format(completedAtFormatter))
        }.toString()

    @Nested
    inner class `given the caller is not authenticated` {
        @Test
        fun `should reject the request`() {
            val response = getCompletedQuizzesWithoutLogin()

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }

    @Nested
    inner class `given no quiz has been completed` {
        @Test
        fun `should return an empty page`() {
            ensureUserExists()

            val response = getCompletedQuizzes()

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(contentOf(response).isEmpty())
        }
    }

    @Nested
    inner class `given quizzes have been completed` {
        @Test
        fun `should return the completion`() {
            val userId = ensureUserExists()
            val quiz = saveQuizEntity("quiz-1", userId)
            val completedAt = OffsetDateTime.of(2025, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC)
            saveCompletionEntity("completion-1", quiz.id, userId, completedAt)

            val response = getCompletedQuizzes()

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(
                completionResponseJson(quiz.id, completedAt),
                contentOf(response).single().toString()
            )
        }

        @Test
        fun `should order completions by most recently completed first`() {

            val userId = ensureUserExists()
            val middleQuiz = saveQuizEntity("quiz-middle", userId)
            val oldestQuiz = saveQuizEntity("quiz-oldest", userId)
            val newestQuiz = saveQuizEntity("quiz-newest", userId)
            val time = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

            // Insertion order: middle, oldest, newest.
            saveCompletionEntity(
                "completion-middle", middleQuiz.id, userId, time
            )
            saveCompletionEntity(
                "completion-oldest", oldestQuiz.id, userId, time.minusMonths(1)
            )
            saveCompletionEntity(
                "completion-newest",newestQuiz.id, userId, time.plusMonths(1)
            )

            val response = getCompletedQuizzes()

            assertEquals(HttpStatus.OK, response.statusCode)
            // Retrieval order:  newest, middle, oldest.
            val ids = contentOf(response).map { it.jsonObject["id"]?.jsonPrimitive?.content }
            assertEquals(listOf(newestQuiz.id, middleQuiz.id, oldestQuiz.id), ids)
        }

        @Test
        fun `should split completions across multiple pages when many exist`() {
            val userId = ensureUserExists()
            (1..11).forEach { index ->
                val quiz = saveQuizEntity("quiz-page-test-$index", userId)
                saveCompletionEntity(
                    "completion-page-test-$index",
                    quiz.id,
                    userId,
                    OffsetDateTime.of(2025, 2, index, 0, 0, 0, 0, ZoneOffset.UTC)
                )
            }

            val firstPage = getCompletedQuizzes(page = 0)
            val secondPage = getCompletedQuizzes(page = 1)

            assertEquals(HttpStatus.OK, firstPage.statusCode)
            assertEquals(HttpStatus.OK, secondPage.statusCode)

            val numberOfCompletionsPage1 = contentOf(firstPage).size
            val numberOfCompletionsPage2 = contentOf(secondPage).size

            assertTrue(numberOfCompletionsPage1 == 10)
            assertTrue(numberOfCompletionsPage2 == 1)
        }
    }
}
