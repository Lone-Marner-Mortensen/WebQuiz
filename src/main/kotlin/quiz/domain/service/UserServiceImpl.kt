package quiz.domain.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import quiz.domain.IdGenerator
import quiz.domain.exception.DuplicateEmailException
import quiz.domain.model.User
import quiz.domain.repository.UserRepository

@Service
class UserServiceImpl(
    private val idGenerator: IdGenerator,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserService {

    override fun registerUser(email: String, rawPassword: String) {
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("Email already taken: $email")
        }
        userRepository.save(
            User(
                id = idGenerator.createId(),
                email = email,
                password = passwordEncoder.encode(rawPassword) ?: throw IllegalArgumentException("Password is null")
            )
        )
    }
}
