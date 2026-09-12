package quiz.domain.service

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import quiz.domain.model.Question
import quiz.domain.model.QuestionDraft
import quiz.domain.model.Quiz
import quiz.domain.exception.InvalidQuizException
import quiz.domain.exception.QuizAuthorMismatchException
import quiz.domain.exception.QuizNotFoundException
import quiz.fakeservice.FakeClock
import quiz.fakeservice.FakeIdGenerator
import quiz.fakeservice.FakeQuizRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

class QuizManagementServiceImplTest {

    private val idGenerator = FakeIdGenerator(id = "generated-id")
    private val clock = FakeClock(now = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
    private val quizRepository = FakeQuizRepository()
    private val service = QuizManagementServiceImpl(idGenerator, clock, quizRepository)

    private val authorEmail = "author@example.com"
    private val otherUserEmail = "other-user@example.com"

    private val twoQuestionQuiz = Quiz(
        id = "quiz-1",
        title = "Geography",
        authorId = authorEmail,
        questions = listOf(
            Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
            Question(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
        ),
        createdAt = OffsetDateTime.now()
    )

    @Nested
    inner class CreateQuiz {
        @Test
        fun `rejects an empty question list`() {
            assertFailsWith<InvalidQuizException> {
                service.createQuiz(title = "Empty", author = "a@example.com", questions = emptyList())
            }
        }

        @Test
        fun `rejects a question with fewer than 2 options`() {
            assertFailsWith<InvalidQuizException> {
                service.createQuiz(
                    title = "Bad",
                    author = "a@example.com",
                    questions = listOf(QuestionDraft(text = "Q1", options = listOf("only one"), answer = 0))
                )
            }
        }

        @Test
        fun `rejects an out-of-range answer index`() {
            assertFailsWith<InvalidQuizException> {
                service.createQuiz(
                    title = "Bad",
                    author = "a@example.com",
                    questions = listOf(QuestionDraft(text = "Q1", options = listOf("a", "b"), answer = 5))
                )
            }
        }

        @Test
        fun `saves a valid multi-question quiz`() {
            val result = service.createQuiz(
                title = "Geography",
                author = "authorId@example.com",
                questions = listOf(
                    QuestionDraft(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
                    QuestionDraft(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
                )
            )

            assertEquals("generated-id", result.id)
            assertEquals("Geography", result.title)
            assertEquals("authorId@example.com", result.authorId)
            assertEquals(
                listOf(
                    Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
                    Question(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
                ),
                result.questions
            )
            assertEquals(result, quizRepository.findById("generated-id"))
        }
    }

    @Nested
    inner class GetQuiz {
        @Test
        fun `throws QuizNotFoundException when missing`() {
            assertFailsWith<QuizNotFoundException> { service.getQuiz("missing") }
        }

        @Test
        fun `returns the quiz when it exists`() {
            quizRepository.save(twoQuestionQuiz)

            val result = service.getQuiz("quiz-1")

            assertEquals(twoQuestionQuiz, result)
        }
    }

    @Nested
    inner class DeleteQuiz {
        @Test
        fun `throws QuizNotFoundException when quiz does not exist`() {
            assertFailsWith<QuizNotFoundException> {
                service.deleteQuiz("missing", "someone@example.com")
            }
        }

        @Test
        fun `throws QuizAuthorMismatchException when requester is not the author`() {
            assertNotEquals(twoQuestionQuiz.authorId, otherUserEmail)
            quizRepository.save(twoQuestionQuiz)

            assertFailsWith<QuizAuthorMismatchException> {
                service.deleteQuiz("quiz-1", requesterEmail = otherUserEmail)
            }
        }

        @Test
        fun `deletes the quiz when requester is the author`() {
            quizRepository.save(twoQuestionQuiz)

            service.deleteQuiz("quiz-1", requesterEmail = twoQuestionQuiz.authorId)

            assertEquals(null, quizRepository.findById("quiz-1"))
        }
    }

    @Nested
    inner class GetAllQuizzes {
        private fun quizWithCreatedAt(id: String, createdAt: OffsetDateTime): Quiz =
            twoQuestionQuiz.copy(id = id, createdAt = createdAt)

        @Test
        fun `returns an empty page when no quiz exists`() {
            val result = service.getAllQuizzes(pageNumber = 0, pageSize = 10)

            assertTrue(result.content.isEmpty())
            assertEquals(0L, result.totalElements)
        }

        @Test
        fun `orders quizzes by most recently created first`() {
            // Insertion order: middle, oldest, newest.
            quizRepository.save(quizWithCreatedAt("quiz-middle", OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
            quizRepository.save(quizWithCreatedAt("quiz-oldest", OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)))
            quizRepository.save(quizWithCreatedAt("quiz-newest", OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC)))

            val result = service.getAllQuizzes(pageNumber = 0, pageSize = 10)

            // Retrieval order: newest, middle, oldest.
            assertEquals(listOf("quiz-newest", "quiz-middle", "quiz-oldest"), result.content.map { it.id })
        }

        @Test
        fun `splits quizzes across multiple pages when many exist`() {
            (1..11).forEach { index ->
                quizRepository.save(
                    quizWithCreatedAt("quiz-$index", OffsetDateTime.of(2025, 1, index, 0, 0, 0, 0, ZoneOffset.UTC))
                )
            }

            val firstPage = service.getAllQuizzes(pageNumber = 0, pageSize = 10)
            val secondPage = service.getAllQuizzes(pageNumber = 1, pageSize = 10)

            assertEquals(10, firstPage.content.size)
            assertEquals(1, secondPage.content.size)
            assertEquals(11L, firstPage.totalElements)
        }
    }
}
