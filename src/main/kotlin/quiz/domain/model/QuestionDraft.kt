package quiz.domain.model

data class QuestionDraft(
    val text: String,
    val options: List<String>,
    val answer: Int
)
