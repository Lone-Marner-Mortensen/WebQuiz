package quiz.infrastructure.security

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import quiz.domain.repository.UserRepository

@Service
class UserDetailsServiceAdapter(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("User not found: $username")
        return AuthenticatedUser(
            id = user.id,
            username = user.email,
            password = user.password
        )
    }
}
