package quiz.repository.mapper

import org.mapstruct.InjectionStrategy
import org.mapstruct.Mapper
import quiz.domain.Question
import quiz.domain.Quiz
import quiz.domain.createId
import quiz.repository.dto.QuestionDto
import quiz.repository.dto.QuizDto

@Mapper(componentModel = "spring", uses = [QuestionDtoMapper::class], injectionStrategy = InjectionStrategy.CONSTRUCTOR)
interface QuizDtoMapper {

    fun toDomain(dto: QuizDto): Quiz

    fun toDto(domain: Quiz): QuizDto {
        val quizDto = QuizDto(
            id = domain.id,
            title = domain.title,
            author = domain.author,
            questions = domain.questions.map { toQuestionDto(it, quiz = null) },
            createdAt = domain.createdAt
        )
        quizDto.questions.forEach { it.quiz = quizDto }
        return quizDto
    }

    private fun toQuestionDto(question: Question, quiz: QuizDto?): QuestionDto {
        return QuestionDto(
            id = createId(),
            text = question.text,
            options = question.options,
            answer = question.answer,
            quiz = quiz
        )
    }
}
