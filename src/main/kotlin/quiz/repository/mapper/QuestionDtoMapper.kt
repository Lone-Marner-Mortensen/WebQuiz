package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.Question
import quiz.repository.dto.QuestionDto

@Mapper(componentModel = "spring")
interface QuestionDtoMapper {
    fun toDomain(dto: QuestionDto): Question
}
