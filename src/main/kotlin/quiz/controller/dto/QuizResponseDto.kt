package quiz.controller.dto

data class QuizResponseDto(
    val id: String,
    val title: String,
    val questions: List<QuestionResponseDto>
)
