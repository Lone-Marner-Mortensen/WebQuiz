package quiz.domain.service

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.domain.model.QuizCompletion
import quiz.domain.exception.InvalidAnswerException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.response.PagedResult
import quiz.fakeservice.FakeClock
import quiz.fakeservice.FakeIdGenerator
import quiz.fakeservice.FakeQuizRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

private class FakeQuizCompletionRepository : QuizCompletionRepository {
    private val completions = mutableListOf<QuizCompletion>()

    override fun save(completion: QuizCompletion): QuizCompletion {
        completions.add(completion)
        return completion
    }

    override fun existsByQuizIdAndUserId(quizId: String, userId: String): Boolean =
        completions.any { it.quizId == quizId && it.userId == userId }

    // Matches QuizCompletionRepositoryImpl.findByUserIdOrderByCompletedAtDesc, which orders by
    // completedAt descending (most recent first).
    override fun findByUserIdOrderByCompletedAtDesc(userId: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion> {
        val sorted = completions.filter { it.userId == userId }.sortedByDescending { it.completedAt }
        val content = sorted.drop(pageNumber * pageSize).take(pageSize)
        return PagedResult(
            content = content,
            pageNumber = pageNumber,
            pageSize = pageSize,
            totalElements = sorted.size.toLong(),
            totalPages = if (sorted.isEmpty()) 0 else (sorted.size + pageSize - 1) / pageSize
        )
    }
}

// JUnit 5 creates a new instance of this class per @Test, so these properties give every test its
// own fresh fake repositories and no state leaks between tests.
class QuizSolvingServiceImplTest {

    private val idGenerator = FakeIdGenerator(id = "generated-id")
    private val clock = FakeClock(now = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
    private val quizRepository = FakeQuizRepository()
    private val quizCompletionRepository = FakeQuizCompletionRepository()
    private val service = QuizSolvingServiceImpl(idGenerator, clock, quizRepository, quizCompletionRepository)

    private val solverEmail = "solver@example.com"

    private val twoQuestionQuiz = Quiz(
        id = "quiz-1",
        title = "Geography",
        authorId = "author@example.com",
        questions = listOf(
            Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
            Question(text = "Capital of Germany?", options = listOf("Paris", "Berlin"), answer = 1)
        ),
        createdAt = OffsetDateTime.now()
    )

    @Nested
    inner class SolveQuiz {
        @Test
        fun `succeeds and records completion when every answer is correct`() {
            quizRepository.save(twoQuestionQuiz)

            val result = service.solveQuiz("quiz-1", listOf(0, 1), solverEmail)

            assertEquals(true, result.success)
            assertEquals(true, quizCompletionRepository.existsByQuizIdAndUserId("quiz-1", solverEmail))
        }

        @Test
        fun `succeeds without recording a duplicate completion when already solved`() {
            quizRepository.save(twoQuestionQuiz)
            service.solveQuiz("quiz-1", listOf(0, 1), solverEmail)

            val result = service.solveQuiz("quiz-1", listOf(0, 1), solverEmail)

            assertEquals(true, result.success)
            val completions = quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(solverEmail, 0, 10)
            assertEquals(1L, completions.totalElements)
        }

        @Test
        fun `fails when any single answer is wrong, and records no completion`() {
            quizRepository.save(twoQuestionQuiz)

            val result = service.solveQuiz("quiz-1", listOf(0, 0), solverEmail)

            assertEquals(false, result.success)
            assertEquals(false, quizCompletionRepository.existsByQuizIdAndUserId("quiz-1", solverEmail))
        }

        @Test
        fun `throws InvalidAnswerException when answer count does not match question count`() {
            quizRepository.save(twoQuestionQuiz)

            assertFailsWith<InvalidAnswerException> {
                service.solveQuiz("quiz-1", listOf(0), solverEmail)
            }
        }

        @Test
        fun `throws QuizNotFoundException when quiz does not exist`() {
            assertFailsWith<QuizNotFoundException> {
                service.solveQuiz("missing", listOf(0), solverEmail)
            }
        }
    }

    @Nested
    inner class GetCompletions {
        @Test
        fun `returns an empty page when no quiz has been completed`() {
            val result = service.getCompletions(solverEmail, pageNumber = 0, pageSize = 10)

            assertEquals(true, result.content.isEmpty())
            assertEquals(0L, result.totalElements)
        }

        @Test
        fun `orders completions by most recently completed first`() {
            quizRepository.save(twoQuestionQuiz)
            // Insertion order: middle, oldest, newest.
            // Retrieval order:  newest, middle, oldest.
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-middle", quizId = "quiz-1", userId = solverEmail, completedAt = OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC))
            )
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-oldest", quizId = "quiz-1", userId = solverEmail, completedAt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
            )
            quizCompletionRepository.save(
                QuizCompletion(id = "completion-newest", quizId = "quiz-1", userId = solverEmail, completedAt = OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC))
            )

            val result = service.getCompletions(solverEmail, pageNumber = 0, pageSize = 10)

            assertEquals(
                listOf("completion-newest", "completion-middle", "completion-oldest"),
                result.content.map { it.id }
            )
        }

        @Test
        fun `splits completions across multiple pages when many exist`() {
            (1..11).forEach { index ->
                quizCompletionRepository.save(
                    QuizCompletion(
                        id = "completion-$index",
                        quizId = "quiz-1",
                        userId = solverEmail,
                        completedAt = OffsetDateTime.of(2025, 1, index, 0, 0, 0, 0, ZoneOffset.UTC)
                    )
                )
            }

            val firstPage = service.getCompletions(solverEmail, pageNumber = 0, pageSize = 10)
            val secondPage = service.getCompletions(solverEmail, pageNumber = 1, pageSize = 10)

            assertEquals(10, firstPage.content.size)
            assertEquals(1, secondPage.content.size)
            assertEquals(11L, firstPage.totalElements)
        }
    }
}
