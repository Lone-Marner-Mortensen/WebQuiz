package engine.quiz.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class CompletionResponse(
    val id: Int,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val completedAt: OffsetDateTime
)
