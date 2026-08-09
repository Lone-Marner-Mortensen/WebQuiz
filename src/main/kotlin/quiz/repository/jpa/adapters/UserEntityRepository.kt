package quiz.repository.jpa.adapters

import org.springframework.data.jpa.repository.JpaRepository
import quiz.repository.dto.UserDto

interface UserEntityRepository : JpaRepository<UserDto, String> {
    fun findByEmail(email: String): UserDto?
    fun existsByEmail(email: String): Boolean
}
