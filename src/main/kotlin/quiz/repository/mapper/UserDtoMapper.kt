package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.model.User
import quiz.repository.entity.UserEntity

@Mapper(componentModel = "spring")
interface UserDtoMapper {
    fun toDomain(dto: UserEntity): User
    fun toDto(domain: User): UserEntity
}
