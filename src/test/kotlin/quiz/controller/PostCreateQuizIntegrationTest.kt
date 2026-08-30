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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals
import quiz.controller.dto.CreateQuizRequestDto
import quiz.controller.dto.QuestionRequestDto
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.model.User
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import quiz.fakeservice.FakeIdGenerator
import tools.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import quiz.fakeservice.FakeClock


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PostCreateQuizIntegrationTest {

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
    private lateinit var clock: FakeClock

    @Autowired
    private lateinit var quizRepository: QuizRepository

    private val objectMapper = ObjectMapper()

    private val email = "quiz@example.com"
    private val password = "password123"

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail(email)) {
            userRepository.save(
                User(
                    id = idGenerator.createId(),
                    email = email,
                    password = passwordEncoder.encode(password) ?: error("Password encoding failed")
                )
            )
        }
    }

    private fun question(
        text: String = "Q",
        options: List<String> = listOf("a", "b"),
        answer: Int = 0
    ) = QuestionRequestDto(text, options, answer)

    private fun quiz(
        title: String = "T",
        questions: List<QuestionRequestDto> = listOf(question())
    ): String = objectMapper.writeValueAsString(CreateQuizRequestDto(title, questions))

    private fun post(body: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate
            .withBasicAuth(email, password)
            .postForEntity("/api/quizzes", entity, String::class.java)
    }

    private fun postwithoutLogin(body: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate.postForEntity("/api/quizzes", entity, String::class.java)
    }

    private fun errorJson(status: Int, error: String, message: String): String =
        buildJsonObject {
            put("status", status)
            put("error", error)
            put("message", message)
        }.toString()

    private fun quizResponseJson(id: String, title: String, questions: List<QuestionRequestDto>, createdAt: String): String =
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
            put("createdAt", createdAt)
        }.toString()

    private fun createdAtOf(body: String): String =
        Regex(""""createdAt":"([^"]+)"""").find(body)?.groupValues?.get(1)
            ?: error("createdAt not found in response body: $body")

    @Nested
    inner class `given the quiz is valid` {
        @Test
        fun `should save the quiz in quizRepository and return it as html-response`() {
            // When
            val id = idGenerator.id
            val questions = listOf(question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0))

            // Then
            val response = post(quiz(title = "Geography", questions = questions))

            // Expect
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body
            assertNotNull(body)
            assertEquals(quizResponseJson(id, "Geography", questions, createdAtOf(body)), body)

            val savedQuiz = quizRepository.findById(id)
            assertNotNull(savedQuiz)
            val expectedQuestions = questions.map { Question(text = it.text, options = it.options, answer = it.answer) }
            val expectedAuthorId = userRepository.findByEmail(email)?.id ?: error("User should exist for email: $email")
            assertEquals(
                Quiz(id = id, title = "Geography", authorId = expectedAuthorId, questions = expectedQuestions, createdAt = clock.now),
                savedQuiz
            )
        }
    }

    @Nested
    inner class `given the caller is not authenticated` {
        @Test
        fun `should reject the request`() {
            val response = postwithoutLogin(quiz())

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }

    @Nested
    inner class `given the title is invalid` {
        @Test
        fun `should reject a blank title`() {
            val response = post(quiz(title = ""))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "title: must not be blank"),
                response.body ?: error("Response body was null")
            )
        }

        @Test
        fun `should reject a title longer than 75 characters`() {
            val response = post(quiz(title = "a".repeat(76)))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "title: Title must be at most 75 characters"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the request body itself is malformed` {
        @Test
        fun `should reject malformed JSON with 400`() {
            // When
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity("""{"title":"T","questions":[{"text":"Q",}]}""", headers)

            // Then
            val response = restTemplate
                .withBasicAuth(email, password)
                .postForEntity("/api/quizzes", entity, String::class.java)

            // Expect
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "MALFORMED_REQUEST", "Request body could not be parsed"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given a question's answer is invalid` {
        @Test
        fun `should reject a null answer field`() {
            val response = post("""{"title":"T","questions":[{"text":"Q","options":["a","b"],"answer":null}]}""")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "MALFORMED_REQUEST", "Request body could not be parsed"),
                response.body ?: error("Response body was null")
            )
        }

        @Test
        fun `should reject an out-of-range answer index`() {
            val response = post(quiz(questions = listOf(question(answer = 9))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "INVALID_QUIZ", "Question 0 answer index 9 is out of range for 2 option(s)"),
                response.body ?: error("Response body was null")
            )
        }

        @Test
        fun `should reject a missing answer field`() {
            val response = post("""{"title":"T","questions":[{"text":"Q","options":["a","b"]}]}""")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "MALFORMED_REQUEST", "Request body could not be parsed"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given a question's answer-options are invalid` {
        @Test
        fun `should reject an answer-option longer than 50 characters`() {
            val response = post(quiz(questions = listOf(question(options = listOf("a".repeat(51), "b")))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "questions[0].options: Each option must be at most 50 characters"),
                response.body ?: error("Response body was null")
            )
        }

        @Test
        fun `should reject fewer than 2 answer-options`() {
            val response = post(quiz(questions = listOf(question(options = listOf("only-one")))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "questions[0].options: There must be at least 2 answer options"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given the number of questions is invalid` {
        @Test
        fun `should reject an empty questions list`() {
            val response = post(quiz(questions = emptyList()))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "INVALID_QUIZ", "Quiz must have at least one question"),
                response.body ?: error("Response body was null")
            )
        }
    }

    @Nested
    inner class `given a question's text is invalid` {
        @Test
        fun `should reject blank question text`() {
            val response = post(quiz(questions = listOf(question(text = ""))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "questions[0].text: must not be blank"),
                response.body ?: error("Response body was null")
            )
        }

        @Test
        fun `should reject question text longer than 100 characters`() {
            val response = post(quiz(questions = listOf(question(text = "a".repeat(101)))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertEquals(
                errorJson(400, "VALIDATION_ERROR", "questions[0].text: Question text must be at most 100 characters"),
                response.body ?: error("Response body was null")
            )
        }
    }
}
