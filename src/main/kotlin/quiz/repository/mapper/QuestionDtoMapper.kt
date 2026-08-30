package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.model.Question
import quiz.repository.entity.QuestionEntity

@Mapper(componentModel = "spring")
interface QuestionDtoMapper {
    fun toDomain(dto: QuestionEntity): Question
}
