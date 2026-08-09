package quiz.controller.dto

data class SolveQuizRequestDto(
    val answers: List<Int> = emptyList()
)
