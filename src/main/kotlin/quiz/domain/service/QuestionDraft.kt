package quiz.domain.service

data class QuestionDraft(
    val text: String,
    val options: List<String>,
    val answer: Int
)
