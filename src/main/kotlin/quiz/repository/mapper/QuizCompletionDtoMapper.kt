package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.QuizCompletion
import quiz.repository.dto.QuizCompletionDto

@Mapper(componentModel = "spring")
interface QuizCompletionDtoMapper {
    fun toDomain(dto: QuizCompletionDto): QuizCompletion
    fun toDto(domain: QuizCompletion): QuizCompletionDto
}
