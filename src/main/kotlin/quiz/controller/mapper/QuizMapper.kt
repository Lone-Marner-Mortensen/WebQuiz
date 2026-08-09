package quiz.controller.mapper

import org.mapstruct.Mapper
import quiz.controller.dto.QuestionResponseDto
import quiz.controller.dto.QuizResponseDto
import quiz.domain.Question
import quiz.domain.Quiz

@Mapper(componentModel = "spring")
interface QuizMapper {
    fun toResponseDto(domain: Quiz): QuizResponseDto
    fun toResponseDto(domain: Question): QuestionResponseDto
}
