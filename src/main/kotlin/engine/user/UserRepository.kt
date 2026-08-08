package engine.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Int> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
