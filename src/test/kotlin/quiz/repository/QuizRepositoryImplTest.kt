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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.model.QuizCompletion
import quiz.domain.model.User
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import quiz.repository.entity.QuestionEntity
import quiz.repository.entity.QuizEntity
import quiz.repository.jpa.adapter.QuizCompletionEntityRepository
import quiz.repository.jpa.adapter.QuizEntityRepository
import quiz.repository.jpa.adapter.UserEntityRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

@SpringBootTest
class QuizRepositoryImplTest {

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
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var quizEntityRepository: QuizEntityRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userEntityRepository: UserEntityRepository

    @Autowired
    private lateinit var quizCompletionRepository: QuizCompletionRepository

    @Autowired
    private lateinit var quizCompletionEntityRepository: QuizCompletionEntityRepository

    @Autowired
    private lateinit var dataSource: DataSource

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

    private fun quiz(id: String, authorId: String, createdAt: OffsetDateTime): Quiz =
        Quiz(
            id = id,
            title = "Geography",
            authorId = authorId,
            questions = listOf(Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0)),
            createdAt = createdAt
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
            QuestionEntity(id = "question-$id", text = "Q", options = listOf("a", "b"), answer = 0, questionOrder = 0)
                .also { it.quiz = quizEntity }
        )
        return quizEntityRepository.save(quizEntity)
    }

    private fun insertQuestion(id: String, quizId: String, text: String, answer: Int, questionOrder: Int) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO questions (id, text, answer, quiz_id, question_order) VALUES (?, ?, ?, ?, ?)"
            ).use { statement ->
                statement.setString(1, id)
                statement.setString(2, text)
                statement.setInt(3, answer)
                statement.setString(4, quizId)
                statement.setInt(5, questionOrder)
                statement.executeUpdate()
            }
        }
    }

    private fun insertQuestionOption(questionId: String, value: String, optionOrder: Int) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO question_options (question_id, option_value, option_order) VALUES (?, ?, ?)"
            ).use { statement ->
                statement.setString(1, questionId)
                statement.setString(2, value)
                statement.setInt(3, optionOrder)
                statement.executeUpdate()
            }
        }
    }

    @Nested
    inner class Save {
        @Test
        fun `persists a quiz and returns it as a domain object`() {
            val authorId = saveUser("author-1", "author@example.com")
            val createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            val newQuiz = quiz(id = "quiz-1", authorId = authorId, createdAt = createdAt)

            val result = quizRepository.save(newQuiz)

            assertEquals(newQuiz, result)
            assertTrue(quizEntityRepository.existsById("quiz-1"))
        }
    }

    @Nested
    inner class FindById {
        @Test
        fun `returns null when quiz does not exist`() {
            assertNull(quizRepository.findById("missing"))
        }

        @Test
        fun `returns the quiz when it exists`() {
            val authorId = saveUser("author-1", "author@example.com")
            val createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            val savedQuiz: Quiz = quizRepository.save(quiz(id = "quiz-1", authorId = authorId, createdAt = createdAt))

            val result = quizRepository.findById("quiz-1")

            assertEquals(savedQuiz, result)
        }
    }

    @Nested
    inner class Ordering {
        @Test
        fun `retrieve questions ordered by question_order, rather than by their physical row insertion order`() {
            val authorId = saveUser("author-1", "author@example.com")
            val createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            quizEntityRepository.save(
                QuizEntity(id = "quiz-1", title = "Geography", authorId = authorId, questions = emptyList(), createdAt = createdAt)
            )

            insertQuestion(id = "question-2", quizId = "quiz-1", text = "Capital of France?", answer = 0, questionOrder = 1)
            insertQuestionOption(questionId = "question-2", value = "Paris", optionOrder = 0)
            insertQuestionOption(questionId = "question-2", value = "Madrid", optionOrder = 1)
            insertQuestion(id = "question-1", quizId = "quiz-1", text = "Capital of Germany?", answer = 0, questionOrder = 0)
            insertQuestionOption(questionId = "question-1", value = "Berlin", optionOrder = 0)
            insertQuestionOption(questionId = "question-1", value = "Rome", optionOrder = 1)

            val result = quizRepository.findById("quiz-1")

            assertEquals(listOf("Capital of Germany?", "Capital of France?"), result?.questions?.map { it.text })
        }

        @Test
        fun `retrieve question-options by option_order, not by physical row insertion order`() {
            val authorId = saveUser("author-1", "author@example.com")
            val createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            quizEntityRepository.save(
                QuizEntity(id = "quiz-1", title = "Geography", authorId = authorId, questions = emptyList(), createdAt = createdAt)
            )
            insertQuestion(id = "question-1", quizId = "quiz-1", text = "Capital of Germany?", answer = 0, questionOrder = 0)
            insertQuestionOption(questionId = "question-1", value = "Amsterdam", optionOrder = 2)
            insertQuestionOption(questionId = "question-1", value = "Rome", optionOrder = 1)
            insertQuestionOption(questionId = "question-1", value = "Berlin", optionOrder = 0)

            val result = quizRepository.findById("quiz-1")

            assertEquals(listOf("Berlin", "Rome", "Amsterdam"), result?.questions?.single()?.options)
        }
    }

    @Nested
    inner class FindAll {
        @Test
        fun `returns an empty page when no quiz exists`() {
            val result = quizRepository.findAll(pageNumber = 0, pageSize = 10)

            assertTrue(result.content.isEmpty())
            assertEquals(0L, result.totalElements)
        }

        @Test
        fun `orders quizzes by most recently created first`() {
            val authorId = saveUser("author-1", "author@example.com")
            // Insertion order: middle, oldest, newest.
            saveQuizEntity("quiz-middle", authorId, OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC))
            saveQuizEntity("quiz-oldest", authorId, OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
            saveQuizEntity("quiz-newest", authorId, OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC))

            val result = quizRepository.findAll(pageNumber = 0, pageSize = 10)

            // Retrieval order:  newest, middle, oldest.
            assertEquals(listOf("quiz-newest", "quiz-middle", "quiz-oldest"), result.content.map { it.id })
        }

        @Test
        fun `splits quizzes across multiple pages when many exist`() {
            val authorId = saveUser("author-1", "author@example.com")
            (1..11).forEach { index ->
                saveQuizEntity("quiz-$index", authorId, OffsetDateTime.of(2025, 1, index, 0, 0, 0, 0, ZoneOffset.UTC))
            }

            val firstPage = quizRepository.findAll(pageNumber = 0, pageSize = 10)
            val secondPage = quizRepository.findAll(pageNumber = 1, pageSize = 10)

            assertEquals(10, firstPage.content.size)
            assertEquals(1, secondPage.content.size)
            assertEquals(11L, firstPage.totalElements)
        }
    }

    @Nested
    inner class DeleteById {
        @Test
        fun `deletes the quiz and any completions recorded for it`() {
            val authorId = saveUser("author-1", "author@example.com")
            val createdAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            quizRepository.save(quiz(id = "quiz-1", authorId = authorId, createdAt = createdAt))
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-1", quizId = "quiz-1", userId = authorId, completedAt = createdAt)
            )

            quizRepository.deleteById("quiz-1")

            assertNull(quizRepository.findById("quiz-1"))
            assertEquals(false, quizCompletionRepository.existsByQuizIdAndUserId("quiz-1", authorId))
        }
    }
}
