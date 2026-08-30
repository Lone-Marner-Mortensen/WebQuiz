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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.model.User
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import quiz.fakeservice.FakeIdGenerator
import quiz.repository.entity.QuestionEntity
import quiz.repository.entity.QuizEntity
import quiz.repository.jpa.adapter.QuizEntityRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GetQuizzesIntegrationTest {

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

    private val email = "quiz-reader@example.com"
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

    private fun saveQuizEntity(id: String, authorId: String, createdAt: OffsetDateTime): QuizEntity {
        val quizEntity = QuizEntity(
            id = id,
            title = "Quiz $id",
            authorId = authorId,
            questions = emptyList(),
            createdAt = createdAt
        )
        quizEntity.questions = listOf(
            QuestionEntity(
                id = "question-$id",
                text = "Q",
                options = listOf("a", "b"),
                answer = 0,
                questionOrder = 0
            ).also { it.quiz = quizEntity }
        )
        return quizEntityRepository.save(quizEntity)
    }

    private fun getQuiz(id: String): ResponseEntity<String> =
        restTemplate.withBasicAuth(email, password).getForEntity("/api/quizzes/$id", String::class.java)

    private fun getQuizzes(page: Int? = null): ResponseEntity<String> {
        val path = if (page != null) "/api/quizzes?page=$page" else "/api/quizzes"
        return restTemplate.withBasicAuth(email, password).getForEntity(path, String::class.java)
    }

    private fun bodyOf(response: ResponseEntity<String>) =
        Json.parseToJsonElement(response.body ?: error("Response body was null")).jsonObject

    private fun contentOf(response: ResponseEntity<String>) =
        bodyOf(response)["content"]?.jsonArray ?: error("content field missing from response: ${response.body}")

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
    inner class `non-quiz responses` {
        @Test
        fun `should return 404 when no quiz exists`() {
            ensureAuthorExists()

            val response = getQuiz("does-not-exist")

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            assertEquals(
                errorJson(404, "QUIZ_NOT_FOUND", "No quiz with id: does-not-exist"),
                response.body ?: error("Response body was null")
            )
        }

        @Test
        fun `should reject the request when the caller is not authenticated`() {
            val response = restTemplate.getForEntity("/api/quizzes/does-not-exist", String::class.java)

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }

        @Test
        fun `should return an empty page when requesting a quiz-page past the end`() {
            ensureAuthorExists()

            val response = getQuizzes(page = 5)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(contentOf(response).isEmpty())
        }
    }

    @Nested
    inner class `quiz responses` {
        @Test
        fun `should return a single page when only 1 quiz exists`() {
            val authorId = ensureAuthorExists()
            val quiz = saveQuiz(authorId)

            val response = getQuizzes(page = 0)

            assertEquals(HttpStatus.OK, response.statusCode)
            val body = bodyOf(response)

            assertEquals("1", body["totalElements"]?.jsonPrimitive?.content)
            assertEquals("1", body["totalPages"]?.jsonPrimitive?.content)
            assertEquals(
                quizResponseJson(quiz.id, quiz.title, quiz.questions, quiz.createdAt),
                contentOf(response).single().toString()
            )
        }

        @Test
        fun `should split quizzes across multiple pages when many quizzes`() {
            val authorId = ensureAuthorExists()
            (1..11).forEach { index ->
                saveQuizEntity(
                    id = "quiz-page-test-$index",
                    authorId = authorId,
                    createdAt = OffsetDateTime.of(2025, 2, index, 0, 0, 0, 0, ZoneOffset.UTC)
                )
            }

            val firstPage = getQuizzes(page = 0)
            val secondPage = getQuizzes(page = 1)

            assertEquals(HttpStatus.OK, firstPage.statusCode)
            assertEquals(HttpStatus.OK, secondPage.statusCode)

            val numberOfQuizzesPage1 = contentOf(firstPage).size
            val numberOfQuizzesPage2 = contentOf(secondPage).size

            assertTrue(numberOfQuizzesPage1 == 10)
            assertTrue(numberOfQuizzesPage2 == 1)
        }
    }
}
