package quiz.repository

import org.springframework.stereotype.Repository
import quiz.domain.model.User
import quiz.domain.repository.UserRepository
import quiz.repository.jpa.adapter.UserEntityRepository
import quiz.repository.mapper.UserDtoMapper

@Repository
class UserRepositoryImpl(
    private val jpaRepository: UserEntityRepository,
    private val mapper: UserDtoMapper
) : UserRepository {

    override fun save(user: User): User =
        mapper.toDto(user)
            .let(jpaRepository::save)
            .let(mapper::toDomain)

    override fun findByEmail(email: String): User? =
        jpaRepository.findByEmail(email)?.let(mapper::toDomain)

    override fun existsByEmail(email: String): Boolean =
        jpaRepository.existsByEmail(email)
}
