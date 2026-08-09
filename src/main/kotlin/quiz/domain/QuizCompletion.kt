package quiz.domain

import java.time.OffsetDateTime

data class QuizCompletion(
    val id: String,
    val quizId: String,
    val userEmail: String,
    val completedAt: OffsetDateTime
)
