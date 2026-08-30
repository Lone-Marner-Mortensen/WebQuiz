package quiz.controller

import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import quiz.controller.dto.RegisterRequestDto
import quiz.domain.service.UserService

@RestController
class UserController(private val userService: UserService) {

    @ApiResponses(
        ApiResponse(responseCode = "200", description = "User registered"),
        ApiResponse(responseCode = "400", description = "Invalid request or email already registered")
    )
    @PostMapping("/api/register")
    fun register(@Valid @RequestBody request: RegisterRequestDto): ResponseEntity<Void> {
        userService.registerUser(request.email, request.password)
        return ResponseEntity.ok().build()
    }
}
