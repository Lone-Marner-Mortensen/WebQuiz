package quiz.repository.mapper

import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import org.springframework.beans.factory.annotation.Autowired
import quiz.domain.IdGenerator
import quiz.domain.model.Question
import quiz.domain.model.Quiz
import quiz.repository.entity.QuestionEntity
import quiz.repository.entity.QuizEntity
import java.time.OffsetDateTime

@Mapper(componentModel = "spring", uses = [QuestionDtoMapper::class], injectionStrategy = InjectionStrategy.CONSTRUCTOR)
abstract class QuizDtoMapper {

    @Autowired
    lateinit var idGenerator: IdGenerator

    abstract fun toDomain(dto: QuizEntity): Quiz

    fun toDto(domain: Quiz): QuizEntity {
        val quizDto = QuizEntity(
            id = domain.id,
            title = domain.title,
            authorId = domain.authorId,
            questions = emptyList(),
            createdAt = domain.createdAt
        )

        quizDto.questions = domain.questions.mapIndexed { index, question ->
            toQuestionDto(question, index).also { it.quiz = quizDto }
        }

        return quizDto
    }

    private fun toQuestionDto(question: Question, questionOrder: Int): QuestionEntity =
        QuestionEntity(
            id = idGenerator.createId(),
            text = question.text,
            options = question.options,
            answer = question.answer,
            questionOrder = questionOrder,
        )
}
