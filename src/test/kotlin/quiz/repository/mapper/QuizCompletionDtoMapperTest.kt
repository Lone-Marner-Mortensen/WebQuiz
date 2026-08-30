package quiz.repository.mapper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import quiz.domain.model.QuizCompletion
import quiz.repository.entity.QuizCompletionEntity
import java.time.OffsetDateTime

class QuizCompletionDtoMapperTest {

    private val mapper: QuizCompletionDtoMapper = QuizCompletionDtoMapperImpl()

    @Test
    fun `toDomain preserves id, quizId, userId and completedAt`() {
        val completedAt = OffsetDateTime.parse("2025-03-01T12:00:00Z")
        val dto = QuizCompletionEntity(
            id = "completion-1",
            quizId = "quiz-1",
            userId = "user-1",
            completedAt = completedAt
        )

        val domain = mapper.toDomain(dto)

        assertEquals(
            QuizCompletion(id = "completion-1", quizId = "quiz-1", userId = "user-1", completedAt = completedAt),
            domain
        )
    }

    @Test
    fun `toDto preserves id, quizId, userId and completedAt`() {
        val completedAt = OffsetDateTime.parse("2025-03-01T12:00:00Z")
        val domain = QuizCompletion(
            id = "completion-1",
            quizId = "quiz-1",
            userId = "user-1",
            completedAt = completedAt
        )

        val dto = mapper.toDto(domain)

        assertEquals("completion-1", dto.id)
        assertEquals("quiz-1", dto.quizId)
        assertEquals("user-1", dto.userId)
        assertEquals(completedAt, dto.completedAt)
        assertNull(dto.quiz)
    }
}
