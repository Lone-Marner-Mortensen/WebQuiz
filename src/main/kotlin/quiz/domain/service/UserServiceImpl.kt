package quiz.domain.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import quiz.domain.repository.IdGenerator
import quiz.domain.exception.DuplicateEmailException
import quiz.domain.model.User
import quiz.domain.repository.UserRepository

@Service
class UserServiceImpl(
    private val idGenerator: IdGenerator,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserService {

    @Transactional
    override fun registerUser(email: String, rawPassword: String) {
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("Email already taken: $email")
        }
        try {
            userRepository.save(
                User(
                    id = idGenerator.createId(),
                    email = email,
                    password = passwordEncoder.encode(rawPassword)!!
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            throw DuplicateEmailException("Email already taken: $email")
        }
    }
}
