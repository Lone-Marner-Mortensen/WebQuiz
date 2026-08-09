package quiz.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import quiz.domain.Question
import quiz.domain.Quiz
import quiz.domain.exception.InvalidAnswerException
import quiz.domain.exception.QuizAuthorMismatchException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository

class QuizServiceImplTest {

    private val quizRepository: QuizRepository = mockk()
    private val quizCompletionRepository: QuizCompletionRepository = mockk()
    private val service = QuizServiceImpl(quizRepository, quizCompletionRepository)

    private val twoQuestionQuiz = Quiz(
        id = "quiz-1",
        title = "Geography",
        author = "author@example.com",
        questions = listOf(
            Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
            Question(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
        )
    )

    @Test
    fun `createQuiz rejects an empty question list`() {
        assertFailsWith<InvalidAnswerException> {
            service.createQuiz(title = "Empty", author = "a@example.com", questions = emptyList())
        }
    }

    @Test
    fun `createQuiz rejects a question with fewer than 2 options`() {
        assertFailsWith<InvalidAnswerException> {
            service.createQuiz(
                title = "Bad",
                author = "a@example.com",
                questions = listOf(QuestionDraft(text = "Q1", options = listOf("only one"), answer = 0))
            )
        }
    }

    @Test
    fun `createQuiz rejects an out-of-range answer index`() {
        assertFailsWith<InvalidAnswerException> {
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
            author = "author@example.com",
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
    fun `solveQuiz succeeds and records completion when every answer is correct`() {
        every { quizRepository.findById("quiz-1") } returns twoQuestionQuiz
        every { quizCompletionRepository.save(any()) } answers { firstArg() }

        val result = service.solveQuiz("quiz-1", listOf(0, 1), "solver@example.com")

        assertEquals(true, result.success)
        verify { quizCompletionRepository.save(any()) }
    }

    @Test
    fun `solveQuiz fails when any single answer is wrong, and records no completion`() {
        every { quizRepository.findById("quiz-1") } returns twoQuestionQuiz

        val result = service.solveQuiz("quiz-1", listOf(0, 0), "solver@example.com")

        assertEquals(false, result.success)
        verify(exactly = 0) { quizCompletionRepository.save(any()) }
    }

    @Test
    fun `solveQuiz throws InvalidAnswerException when answer count does not match question count`() {
        every { quizRepository.findById("quiz-1") } returns twoQuestionQuiz

        assertFailsWith<InvalidAnswerException> {
            service.solveQuiz("quiz-1", listOf(0), "solver@example.com")
        }
    }

    @Test
    fun `solveQuiz throws QuizNotFoundException when quiz does not exist`() {
        every { quizRepository.findById("missing") } returns null

        assertFailsWith<QuizNotFoundException> {
            service.solveQuiz("missing", listOf(0), "solver@example.com")
        }
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
            service.deleteQuiz("quiz-1", "not-the-author@example.com")
        }
        verify(exactly = 0) { quizRepository.deleteById(any()) }
    }
}
