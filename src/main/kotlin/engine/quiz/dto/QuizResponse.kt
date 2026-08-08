package engine.quiz.dto

data class QuizResponse(
    val id: Int,
    val title: String,
    val text: String,
    val options: List<String>
)
