package quiz.controller

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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import quiz.controller.dto.CreateQuizRequestDto
import quiz.controller.dto.QuestionRequestDto
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository
import quiz.testsupport.AbstractPostgresIntegrationTest
import tools.jackson.databind.ObjectMapper

//
//
// NOT READY FOR REVIEW
//
//
//

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestClassOrder(ClassOrderer.OrderAnnotation::class)
class PostCreateQuizIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private val objectMapper = ObjectMapper()

    private val email = "quiz@example.com"
    private val password = "password123"

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail(email)) {
            userRepository.save(
                User(
                    id = createId(),
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

    private fun post(body: String): org.springframework.http.ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate
            .withBasicAuth(email, password)
            .postForEntity("/api/quizzes", entity, String::class.java)
    }

    private fun postwithoutLogin(body: String): org.springframework.http.ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(body, headers)
        return restTemplate.postForEntity("/api/quizzes", entity, String::class.java)
    }

    @Nested
    @Order(1)
    inner class `when the quiz is valid` {
        @Test
        fun `should create a single-question quiz`() {
            val response = post(
                quiz(
                    title = "Geography",
                    questions = listOf(question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0))
                )
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body ?: error("Response body was null")
            assertTrue(body.contains("\"title\":\"Geography\""))
            assertTrue(body.contains("\"text\":\"Capital of France?\""))
            assertTrue(body.contains("\"options\":[\"Paris\",\"Berlin\"]"))
        }

        @Test
        fun `should create a multi-question quiz and preserve question order`() {
            val response = post(
                quiz(
                    title = "Multi",
                    questions = listOf(
                        question(text = "Question A", options = listOf("a1", "a2"), answer = 0),
                        question(text = "Question B", options = listOf("b1", "b2"), answer = 1),
                        question(text = "Question C", options = listOf("c1", "c2"), answer = 0)
                    )
                )
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body ?: error("Response body was null")
            val indexA = body.indexOf("Question A")
            val indexB = body.indexOf("Question B")
            val indexC = body.indexOf("Question C")
            assertTrue(indexA in 0..<indexB && indexB in 0..<indexC)
        }
    }

    @Nested
    @Order(2)
    inner class `when the caller is not authenticated` {
        @Test
        fun `should reject the request`() {
            val response = postwithoutLogin(quiz())

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }

    @Nested
    @Order(3)
    inner class `when the title is invalid` {
        @Test
        fun `should reject a blank title`() {
            val response = post(quiz(title = ""))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("VALIDATION_ERROR"))
        }

        @Test
        fun `should reject a title longer than 75 characters`() {
            val response = post(quiz(title = "a".repeat(76)))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("Title must be at most 75 characters"))
        }
    }

    @Nested
    @Order(4)
    inner class `when the request body itself is malformed` {
        @Test
        fun `should reject malformed JSON with 400`() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity("""{"title":"T","questions":[{"text":"Q",}]}""", headers)
            val response = restTemplate
                .withBasicAuth(email, password)
                .postForEntity("/api/quizzes", entity, String::class.java)

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("MALFORMED_REQUEST"))
        }
    }

    @Nested
    @Order(5)
    inner class `when a question's answer is invalid` {
        @Test
        fun `should reject a null answer field`() {
            val response = post("""{"title":"T","questions":[{"text":"Q","options":["a","b"],"answer":null}]}""")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("MALFORMED_REQUEST"))
        }

        @Test
        fun `should reject an out-of-range answer index`() {
            val response = post(quiz(questions = listOf(question(answer = 9))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("INVALID_ANSWER"))
        }

        @Test
        fun `should reject a missing answer field`() {
            val response = post("""{"title":"T","questions":[{"text":"Q","options":["a","b"]}]}""")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("MALFORMED_REQUEST"))
        }
    }

    @Nested
    @Order(6)
    inner class `when a question's answer-options are invalid` {
        @Test
        fun `should reject an answer-option longer than 50 characters`() {
            val response = post(quiz(questions = listOf(question(options = listOf("a".repeat(51), "b")))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("Each option must be at most 50 characters"))
        }

        @Test
        fun `should reject fewer than 2 answer-options`() {
            val response = post(quiz(questions = listOf(question(options = listOf("only-one")))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("There must be at least 2 answer options"))
        }
    }

    @Nested
    @Order(7)
    inner class `when the number of questions is invalid` {
        @Test
        fun `should reject more than 7 questions`() {
            val response = post(quiz(questions = List(8) { question() }))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("Quiz must have at least 1 and at most 7 questions"))
        }

        @Test
        fun `should reject an empty questions list`() {
            val response = post(quiz(questions = emptyList()))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("Quiz must have at least 1 and at most 7 questions"))
        }
    }

    @Nested
    @Order(8)
    inner class `when a question's text is invalid` {
        @Test
        fun `should reject blank question text`() {
            val response = post(quiz(questions = listOf(question(text = ""))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("VALIDATION_ERROR"))
        }

        @Test
        fun `should reject question text longer than 100 characters`() {
            val response = post(quiz(questions = listOf(question(text = "a".repeat(101)))))

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            assertTrue((response.body ?: error("Response body was null")).contains("Question text must be at most 100 characters"))
        }
    }
}
