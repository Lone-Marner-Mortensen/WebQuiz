package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.model.QuizCompletion
import quiz.repository.entity.QuizCompletionEntity

@Mapper(componentModel = "spring")
interface QuizCompletionDtoMapper {
    fun toDomain(dto: QuizCompletionEntity): QuizCompletion
    fun toDto(domain: QuizCompletion): QuizCompletionEntity
}
