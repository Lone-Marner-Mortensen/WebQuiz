package quiz.controller.error

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String
)
