package quiz.domain.model

import java.time.OffsetDateTime

data class QuizCompletion(
    val id: String,
    val quizId: String,
    val userId: String,
    val completedAt: OffsetDateTime
)
