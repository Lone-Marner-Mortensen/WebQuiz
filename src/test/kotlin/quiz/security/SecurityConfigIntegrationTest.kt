package quiz.security

import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertEquals
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository
import quiz.testsupport.AbstractPostgresIntegrationTest


//
//
// NOT READY FOR REVIEW
//
//
//

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SecurityConfigIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail("matrix-user@example.com")) {
            userRepository.save(
                User(
                    id = createId(),
                    email = "matrix-user@example.com",
                    password = passwordEncoder.encode("password123") ?: error("Password encoding failed")
                )
            )
        }
    }

    @Test
    fun `GET quizzes list without auth is rejected`() {
        val response = restTemplate.getForEntity("/api/quizzes", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `GET single quiz by id without auth is rejected`() {
        val response = restTemplate.getForEntity("/api/quizzes/does-not-exist", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST solve without auth is rejected`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity("{\"answers\":[]}", headers)
        val response = restTemplate.postForEntity("/api/quizzes/does-not-exist/solve", entity, String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST solve with auth is not rejected for auth reasons`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity("{\"answers\":[]}", headers)
        val response = restTemplate
            .withBasicAuth("matrix-user@example.com", "password123")
            .postForEntity("/api/quizzes/does-not-exist/solve", entity, String::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST create quiz without auth is rejected`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity("{}", headers)
        val response = restTemplate.postForEntity("/api/quizzes", entity, String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST create quiz with auth is not rejected for auth reasons`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity("{}", headers)
        val response = restTemplate
            .withBasicAuth("matrix-user@example.com", "password123")
            .postForEntity("/api/quizzes", entity, String::class.java)
        // Body is empty/invalid so it will fail validation (400), but must NOT be 401/403.
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET completed quizzes without auth is rejected`() {
        val response = restTemplate.getForEntity("/api/quizzes/completed", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST register without auth is not rejected for auth reasons`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity("{}", headers)
        val response = restTemplate.postForEntity("/api/register", entity, String::class.java)
        // Body is empty/invalid so it will fail validation (400), but must NOT be 401.
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }
}
