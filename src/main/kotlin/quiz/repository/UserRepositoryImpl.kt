package quiz.repository

import org.springframework.stereotype.Repository
import quiz.domain.User
import quiz.domain.repository.UserRepository
import quiz.repository.jpa.adapters.UserEntityRepository
import quiz.repository.mapper.UserDtoMapper

@Repository
class UserRepositoryImpl(
    private val jpaRepository: UserEntityRepository,
    private val mapper: UserDtoMapper
) : UserRepository {

    override fun save(user: User): User {
        return mapper.toDomain(jpaRepository.save(mapper.toDto(user)))
    }

    override fun findByEmail(email: String): User? {
        return jpaRepository.findByEmail(email)?.let { mapper.toDomain(it) }
    }

    override fun existsByEmail(email: String): Boolean {
        return jpaRepository.existsByEmail(email)
    }
}
