package quiz.controller.mapper

import org.mapstruct.Mapper
import quiz.controller.dto.CreateQuizResponseDto
import quiz.controller.dto.QuestionResponseDto
import quiz.domain.model.Question
import quiz.domain.model.Quiz

@Mapper(componentModel = "spring")
interface QuizMapper {
    fun toResponseDto(domain: Quiz): CreateQuizResponseDto
    fun toResponseDto(domain: Question): QuestionResponseDto
}
