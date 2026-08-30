package quiz.repository.mapper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import quiz.domain.IdGenerator
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.repository.entity.QuestionEntity
import quiz.repository.entity.QuizEntity
import java.time.OffsetDateTime
import java.util.UUID

class QuizDtoMapperTest {

    private val fakeIdGenerator = object : IdGenerator {
        override fun createId(): String = UUID.randomUUID().toString()
    }
    private val mapper: QuizDtoMapper = QuizDtoMapperImpl(QuestionDtoMapperImpl()).apply {
        idGenerator = fakeIdGenerator
    }

    @Test
    fun `toDto generates a distinct id per question and preserves order`() {
        val quiz = Quiz(
            id = "quiz-1",
            title = "Geography",
            authorId = "author@example.com",
            questions = listOf(
                Question(text = "Q1", options = listOf("a", "b"), answer = 0),
                Question(text = "Q2", options = listOf("c", "d"), answer = 1)
            ),
            createdAt = OffsetDateTime.now()
        )

        val dto = mapper.toDto(quiz)

        assertEquals(2, dto.questions.size)
        assertEquals("Q1", dto.questions[0].text)
        assertEquals("Q2", dto.questions[1].text)
        assertEquals(2, dto.questions.map { it.id }.toSet().size)
    }

    @Test
    fun `toDomain drops question id and preserves order`() {
        val quizDto = QuizEntity(
            id = "quiz-1",
            title = "Geography",
            authorId = "author@example.com",
            questions = listOf(
                QuestionEntity(id = "q1", text = "Q1", options = listOf("a", "b"), answer = 0),
                QuestionEntity(id = "q2", text = "Q2", options = listOf("c", "d"), answer = 1)
            ),
            createdAt = OffsetDateTime.now()
        )

        val domain = mapper.toDomain(quizDto)

        assertEquals(2, domain.questions.size)
        assertEquals("Q1", domain.questions[0].text)
        assertEquals("Q2", domain.questions[1].text)
    }
}
