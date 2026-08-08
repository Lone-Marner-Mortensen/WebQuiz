package engine.quiz.dto

data class SolveQuizRequest(
    val answer: List<Int> = emptyList()
)
