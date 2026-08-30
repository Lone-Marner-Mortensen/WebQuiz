package quiz.controller.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class CreateQuizResponseDto(
    val id: String,
    val title: String,
    val questions: List<QuestionResponseDto>,

    @param:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val createdAt: OffsetDateTime
)
