package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.User
import quiz.repository.dto.UserDto

@Mapper(componentModel = "spring")
interface UserDtoMapper {
    fun toDomain(dto: UserDto): User
    fun toDto(domain: User): UserDto
}
