package quiz.repository

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import quiz.domain.model.QuizCompletion
import quiz.domain.model.User
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.UserRepository
import quiz.repository.entity.QuizEntity
import quiz.repository.jpa.adapter.QuizCompletionEntityRepository
import quiz.repository.jpa.adapter.QuizEntityRepository
import quiz.repository.jpa.adapter.UserEntityRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
class QuizCompletionRepositoryImplTest {

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
    private lateinit var quizCompletionRepository: QuizCompletionRepository

    @Autowired
    private lateinit var quizCompletionEntityRepository: QuizCompletionEntityRepository

    @Autowired
    private lateinit var quizEntityRepository: QuizEntityRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userEntityRepository: UserEntityRepository

    @AfterEach
    fun tearDown() {
        quizCompletionEntityRepository.deleteAll()
        quizEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
    }

    private fun saveUser(id: String, email: String): String {
        userRepository.save(User(id = id, email = email, password = "irrelevant"))
        return id
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

    @Nested
    inner class Save {
        @Test
        fun `persists a completion that can be found by quiz and user id`() {
            val userId = saveUser("user-1", "solver@example.com")
            val quiz = saveQuizEntity("quiz-1", userId)
            val completedAt = OffsetDateTime.of(2025, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC)

            quizCompletionRepository.save(
                QuizCompletion(id = "completion-1", quizId = quiz.id, userId = userId, completedAt = completedAt)
            )

            assertTrue(quizCompletionRepository.existsByQuizIdAndUserId(quiz.id, userId))
        }
    }

    @Nested
    inner class ExistsByQuizIdAndUserId {
        @Test
        fun `returns true when a completion is recorded for the pair`() {
            val userId = saveUser("user-1", "solver@example.com")
            val quiz = saveQuizEntity("quiz-1", userId)
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-1", quizId = quiz.id, userId = userId, completedAt = OffsetDateTime.now())
            )

            assertEquals(true, quizCompletionRepository.existsByQuizIdAndUserId(quiz.id, userId))
        }

        @Test
        fun `returns false when no completion is recorded for the pair`() {
            val userId = saveUser("user-1", "solver@example.com")
            val quiz = saveQuizEntity("quiz-1", userId)

            assertEquals(false, quizCompletionRepository.existsByQuizIdAndUserId(quiz.id, userId))
        }
    }

    @Nested
    inner class FindByUserIdOrderByCompletedAtDesc {
        @Test
        fun `returns an empty page when the user has no completions`() {
            val userId = saveUser("user-1", "solver@example.com")

            val result = quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(userId, pageNumber = 0, pageSize = 10)

            assertTrue(result.content.isEmpty())
            assertEquals(0L, result.totalElements)
        }

        @Test
        fun `only returns completions belonging to the requested user`() {
            val userId = saveUser("user-1", "solver@example.com")
            val otherUserId = saveUser("user-2", "other-solver@example.com")
            val quiz = saveQuizEntity("quiz-1", userId)
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-mine", quizId = quiz.id, userId = userId, completedAt = OffsetDateTime.now())
            )
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-other", quizId = quiz.id, userId = otherUserId, completedAt = OffsetDateTime.now())
            )

            val result = quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(userId, pageNumber = 0, pageSize = 10)

            assertEquals(listOf("completion-mine"), result.content.map { it.id })
        }

        @Test
        fun `orders completions by most recently completed first`() {
            val userId = saveUser("user-1", "solver@example.com")
            val middleQuiz = saveQuizEntity("quiz-middle", userId)
            val oldestQuiz = saveQuizEntity("quiz-oldest", userId)
            val newestQuiz = saveQuizEntity("quiz-newest", userId)
            val time = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

            // Insertion order: middle, oldest, newest.
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-middle", quizId = middleQuiz.id, userId = userId, completedAt = time)
            )
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-oldest", quizId = oldestQuiz.id, userId = userId, completedAt = time.minusMonths(1))
            )
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-newest", quizId = newestQuiz.id, userId = userId, completedAt = time.plusMonths(1))
                )

            val result = quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(userId, pageNumber = 0, pageSize = 10)

            // Retrieval order: newest, middle, oldest.
            assertEquals(
                listOf("completion-newest", "completion-middle", "completion-oldest"),
                result.content.map { it.id }
            )
        }

        @Test
        fun `splits completions across multiple pages when many exist`() {
            val userId = saveUser("user-1", "solver@example.com")
            (1..11).forEach { index ->
                val quiz = saveQuizEntity("quiz-$index", userId)
                quizCompletionRepository.save(
                    QuizCompletion(
                        id = "completion-$index",
                        quizId = quiz.id,
                        userId = userId,
                        completedAt = OffsetDateTime.of(2025, 1, index, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
                )
            }

            val firstPage = quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(userId, pageNumber = 0, pageSize = 10)
            val secondPage = quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(userId, pageNumber = 1, pageSize = 10)

            assertEquals(10, firstPage.content.size)
            assertEquals(1, secondPage.content.size)
            assertEquals(11L, firstPage.totalElements)
        }
    }
}
