package quiz.controller.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateQuizRequestDto(
    @field:NotBlank
    @field:NotNull
    @field:Size(max = 75, message = "Title must be at most 75 characters")
    val title: String,

    @field:NotNull
    @field:Valid
    val questions: List<QuestionRequestDto>
)
