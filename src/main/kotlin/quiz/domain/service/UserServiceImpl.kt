package quiz.domain.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import quiz.domain.exception.DuplicateEmailException
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserService {

    override fun registerUser(email: String, rawPassword: String) {
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("Email already taken: $email")
        }
        userRepository.save(
            User(
                id = createId(),
                email = email,
                password = passwordEncoder.encode(rawPassword) ?: ""
            )
        )
    }
}
