package engine.controller

import engine.user.UserService
import engine.user.dto.RegisterRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(private val userService: UserService) {

    @PostMapping("/api/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Void> {
        userService.registerUser(request)
        return ResponseEntity.ok().build()
    }
}
