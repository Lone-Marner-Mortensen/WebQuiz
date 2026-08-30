package quiz.repository.jpa.adapter

import org.springframework.data.jpa.repository.JpaRepository
import quiz.repository.entity.UserEntity

interface UserEntityRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
}
