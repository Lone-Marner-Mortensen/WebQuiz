package quiz.controller.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import quiz.controller.validation.MaxElementLength

data class QuestionRequestDto(
    @field:NotBlank
    @field:NotNull
    @field:Size(max = 100, message = "Question text must be at most 100 characters")
    val text: String,

    @field:NotNull
    @field:Size(min = 2, message = "There must be at least 2 answer options")
    @field:MaxElementLength(max = 50, message = "Each option must be at most 50 characters")
    val options: List<String>,

    @field:NotNull
    val answer: Int
)
