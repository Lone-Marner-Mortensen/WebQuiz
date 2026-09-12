package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.model.User
import quiz.repository.entity.UserEntity

// `toDto` is hand-written and not than MapStruct-generated. `UserEntity` is a JPA entity with a
// Hibernate-required synthetic no-arg constructor (from kotlin-jpa); MapStruct picks that constructor over
// the real one and leaves every field null, since `UserEntity` has no setters to fill them in afterward.
@Mapper(componentModel = "spring")
interface UserDtoMapper {
    fun toDomain(dto: UserEntity): User

    fun toDto(domain: User): UserEntity =
        UserEntity(
            id = domain.id,
            email = domain.email,
            password = domain.password
        )
}
