package quiz.repository.mapper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import quiz.domain.Question
import quiz.domain.Quiz
import quiz.repository.dto.QuestionDto
import quiz.repository.dto.QuizDto
import java.time.OffsetDateTime


//
//
// NOT READY FOR REVIEW
//
//
//

class QuizDtoMapperTest {

    private val mapper: QuizDtoMapper = QuizDtoMapperImpl(QuestionDtoMapperImpl())

    @Test
    fun `toDto generates a distinct id per question and preserves order`() {
        val quiz = Quiz(
            id = "quiz-1",
            title = "Geography",
            author = "author@example.com",
            questions = listOf(
                Question(text = "Q1", options = listOf("a", "b"), answer = 0),
                Question(text = "Q2", options = listOf("c", "d"), answer = 1)
            )
        )

        val dto = mapper.toDto(quiz)

        assertEquals(2, dto.questions.size)
        assertEquals("Q1", dto.questions[0].text)
        assertEquals("Q2", dto.questions[1].text)
        assertEquals(2, dto.questions.map { it.id }.toSet().size)
        dto.questions.forEach { assertEquals(dto, it.quiz) }
    }

    @Test
    fun `toDomain drops question id and preserves order`() {
        val quizDto = QuizDto(
            id = "quiz-1",
            title = "Geography",
            author = "author@example.com",
            questions = listOf(
                QuestionDto(id = "q1", text = "Q1", options = listOf("a", "b"), answer = 0, quiz = null),
                QuestionDto(id = "q2", text = "Q2", options = listOf("c", "d"), answer = 1, quiz = null)
            ),
            createdAt = OffsetDateTime.now()
        )

        val domain = mapper.toDomain(quizDto)

        assertEquals(2, domain.questions.size)
        assertEquals("Q1", domain.questions[0].text)
        assertEquals("Q2", domain.questions[1].text)
    }
}
