package quiz.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterRequestDto(
    @field:NotBlank
    @field:Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$", message = "invalid email address")
    @field:NotNull
    @field:Schema(example = "user@example.com")
    val email: String,

    @field:NotBlank
    @field:NotNull
    @field:Size(min = 5, message = "Password must be at least 5 characters long")
    @field:Schema(example = "password123")
    val password: String
)
