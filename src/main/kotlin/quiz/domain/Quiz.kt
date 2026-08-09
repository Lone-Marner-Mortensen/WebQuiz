package quiz.domain

import java.time.OffsetDateTime

data class Quiz(
    val id: String,
    val title: String,
    val author: String,
    val questions: List<Question>,
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
