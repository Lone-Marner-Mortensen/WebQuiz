package quiz.controller.mapper

import org.mapstruct.Mapper
import org.mapstruct.Mapping
import quiz.controller.dto.QuizCompletionResponseDto
import quiz.domain.model.QuizCompletion

@Mapper(componentModel = "spring")
interface CompletionMapper {
    @Mapping(source = "quizId", target = "id")
    fun toResponseDto(domain: QuizCompletion): QuizCompletionResponseDto
}
