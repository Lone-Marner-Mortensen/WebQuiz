package quiz.controller.mapper

import org.mapstruct.Mapper
import quiz.controller.dto.AnswerResultDto
import quiz.domain.response.AnswerResult

@Mapper(componentModel = "spring")
interface AnswerMapper {
    fun toResponseDto(domain: AnswerResult): AnswerResultDto
}
