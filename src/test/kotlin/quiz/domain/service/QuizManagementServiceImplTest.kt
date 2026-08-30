package quiz.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.Clock
import quiz.domain.IdGenerator
import quiz.domain.exception.InvalidQuizException
import quiz.domain.exception.QuizAuthorMismatchException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.repository.QuizRepository
import java.time.OffsetDateTime

class QuizManagementServiceImplTest {

    private val idGenerator: IdGenerator = mockk {
        every { createId() } returns "generated-id"
    }
    private val clock: Clock = mockk {
        every { now() } returns OffsetDateTime.now()
    }
    private val quizRepository: QuizRepository = mockk()
    private val service = QuizManagementServiceImpl(idGenerator, clock, quizRepository)

    private val twoQuestionQuiz = Quiz(
        id = "quiz-1",
        title = "Geography",
        authorId = "authorId@example.com",
        questions = listOf(
            Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
            Question(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
        ),
        createdAt = OffsetDateTime.now()
    )

    @Test
    fun `createQuiz rejects an empty question list`() {
        assertFailsWith<InvalidQuizException> {
            service.createQuiz(title = "Empty", author = "a@example.com", questions = emptyList())
        }
    }

    @Test
    fun `createQuiz rejects a question with fewer than 2 options`() {
        assertFailsWith<InvalidQuizException> {
            service.createQuiz(
                title = "Bad",
                author = "a@example.com",
                questions = listOf(QuestionDraft(text = "Q1", options = listOf("only one"), answer = 0))
            )
        }
    }

    @Test
    fun `createQuiz rejects an out-of-range answer index`() {
        assertFailsWith<InvalidQuizException> {
            service.createQuiz(
                title = "Bad",
                author = "a@example.com",
                questions = listOf(QuestionDraft(text = "Q1", options = listOf("a", "b"), answer = 5))
            )
        }
    }

    @Test
    fun `createQuiz saves a valid multi-question quiz`() {
        every { quizRepository.save(any()) } answers { firstArg() }

        val result = service.createQuiz(
            title = "Geography",
            author = "authorId@example.com",
            questions = listOf(
                QuestionDraft(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
                QuestionDraft(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
            )
        )

        assertEquals(2, result.questions.size)
        assertEquals("Capital of France?", result.questions[0].text)
        verify { quizRepository.save(any()) }
    }

    @Test
    fun `getQuiz throws QuizNotFoundException when missing`() {
        every { quizRepository.findById("missing") } returns null

        assertFailsWith<QuizNotFoundException> { service.getQuiz("missing") }
    }

    @Test
    fun `deleteQuiz throws QuizNotFoundException when quiz does not exist`() {
        every { quizRepository.findById("missing") } returns null

        assertFailsWith<QuizNotFoundException> {
            service.deleteQuiz("missing", "someone@example.com")
        }
    }

    @Test
    fun `deleteQuiz throws QuizAuthorMismatchException when requester is not the author`() {
        every { quizRepository.findById("quiz-1") } returns twoQuestionQuiz

        assertFailsWith<QuizAuthorMismatchException> {
            service.deleteQuiz("quiz-1", "not-the-authorId@example.com")
        }
        verify(exactly = 0) { quizRepository.deleteById(any()) }
    }
}
