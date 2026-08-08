package engine.quiz.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateQuizRequest(
    @field:NotBlank
    val title: String?,

    @field:NotBlank
    val text: String?,

    @field:NotNull
    @field:Size(min = 2, message = "Options must contain at least 2 items")
    val options: List<String>?,

    val answer: List<Int>?
)
