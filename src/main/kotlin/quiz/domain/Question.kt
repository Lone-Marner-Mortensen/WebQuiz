package quiz.domain

data class Question(
    val text: String,
    val options: List<String>,
    val answer: Int
)
