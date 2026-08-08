package engine.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    @field:Pattern(regexp = ".+@.+\\..+", message = "must be a well-formed email address")
    val email: String?,

    @field:NotBlank
    @field:Size(min = 5, message = "Password must be at least 5 characters long")
    val password: String?
)
