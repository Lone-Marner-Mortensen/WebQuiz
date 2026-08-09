package quiz.controller.dto

data class QuestionResponseDto(
    val text: String,
    val options: List<String>,
    val answer: Int
)
