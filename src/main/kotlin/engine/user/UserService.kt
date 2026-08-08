package engine.user

import engine.user.dto.RegisterRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun registerUser(request: RegisterRequest) {
        val email = request.email!!
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("Email already taken: $email")
        }
        val user = User(
            email = email,
            password = passwordEncoder.encode(request.password!!)!!
        )
        userRepository.save(user)
    }
}
