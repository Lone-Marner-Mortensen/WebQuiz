package quiz.controller.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class QuizCompletionResponseDto(
    val id: String,

    @param:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val completedAt: OffsetDateTime
)
