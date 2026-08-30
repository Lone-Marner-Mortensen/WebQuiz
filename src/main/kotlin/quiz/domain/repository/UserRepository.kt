package quiz.domain.repository

import quiz.domain.model.User

interface UserRepository {
    fun save(user: User): User
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
