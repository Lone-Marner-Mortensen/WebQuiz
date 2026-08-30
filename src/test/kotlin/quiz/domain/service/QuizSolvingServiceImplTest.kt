package quiz.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.model.QuizCompletion
import quiz.domain.Clock
import quiz.domain.IdGenerator
import quiz.domain.exception.InvalidAnswerException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository
import quiz.domain.response.PagedResult
import java.time.OffsetDateTime

class QuizSolvingServiceImplTest {

    private val idGenerator: IdGenerator = mockk {
        every { createId() } returns "generated-id"
    }
    private val clock: Clock = mockk {
        every { now() } returns OffsetDateTime.now()
    }
    private val quizRepository: QuizRepository = mockk()
    private val quizCompletionRepository: QuizCompletionRepository = mockk()
    private val service = QuizSolvingServiceImpl(idGenerator, clock, quizRepository, quizCompletionRepository)

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
    fun `solveQuiz succeeds and records completion when every answer is correct`() {
        every { quizRepository.findById("quiz-1") } returns twoQuestionQuiz
        every { quizCompletionRepository.existsByQuizIdAndUserId("quiz-1", "solver@example.com") } returns false
        every { quizCompletionRepository.save(any()) } answers { firstArg() }

        val result = service.solveQuiz("quiz-1", listOf(0, 1), "solver@example.com")

        assertEquals(true, result.success)
        verify { quizCompletionRepository.save(any()) }
    }

    @Test
    fun `solveQuiz succeeds without recording duplicate completion`() {
        every { quizRepository.findById("quiz-1") } returns twoQuestionQuiz
        every { quizCompletionRepository.existsByQuizIdAndUserId("quiz-1", "solver@example.com") } returns true

        val result = service.solveQuiz("quiz-1", listOf(0, 1), "solver@example.com")

        assertEquals(true, result.success)
        verify(exactly = 0) { quizCompletionRepository.save(any()) }
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
    fun `getCompletions returns completions from repository`() {
        // When
        val expected = PagedResult(
            content = listOf(
                QuizCompletion(
                    id = "completion-1",
                    quizId = "quiz-1",
                    userId = "solver@example.com",
                    completedAt = OffsetDateTime.parse("2026-08-28T12:00:00Z")
                )
            ),
            pageNumber = 2,
            pageSize = 10,
            totalElements = 1,
            totalPages = 1
        )
        every {
            quizCompletionRepository.findByUserIdOrderByCompletedAtDesc("solver@example.com", 2, 10)
        } returns expected

        // Then
        val result = service.getCompletions("solver@example.com", 2, 10)

        // Expect
        assertEquals(expected, result)
        verify {
            quizCompletionRepository.findByUserIdOrderByCompletedAtDesc("solver@example.com", 2, 10)
        }
    }
}
