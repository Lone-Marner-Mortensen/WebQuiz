package quiz.repository.mapper

import org.junit.jupiter.api.Test
import quiz.domain.model.Question
import kotlin.test.assertEquals
import quiz.repository.entity.QuestionEntity

class QuestionDtoMapperTest {

    private val mapper: QuestionDtoMapper = QuestionDtoMapperImpl()

    @Test
    fun `toDomain drops id, quiz and questionOrder, keeping text, options and answer`() {
        val dto = QuestionEntity(
            id = "question-1",
            text = "Capital of France?",
            options = listOf("Paris", "Berlin"),
            answer = 0,
            questionOrder = 3
        )

        val domain: Question = mapper.toDomain(dto)

        assertEquals(
            Question(text = "Capital of France?", options = listOf("Paris", "Berlin"), answer = 0),
            domain
        )
    }
}
