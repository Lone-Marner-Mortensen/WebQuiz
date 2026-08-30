package quiz.domain.model

import java.time.OffsetDateTime

data class Quiz(
    val id: String,
    val title: String,
    val authorId: String,
    val questions: List<Question>,
    val createdAt: OffsetDateTime
) {
    init {
        require(title.isNotBlank() && title.length <= 75) {
            "Quiz title must contain between 1 and 75 characters"
        }
        require(authorId.isNotBlank()) { "Quiz author ID must not be blank" }
        require(questions.size in 1..7) { "Quiz must have between 1 and 7 questions" }
    }
}
