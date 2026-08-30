package quiz.repository.mapper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import quiz.domain.model.User
import quiz.repository.entity.UserEntity

class UserDtoMapperTest {

    private val mapper: UserDtoMapper = UserDtoMapperImpl()

    @Test
    fun `toDomain preserves id, email and password`() {
        val dto = UserEntity(id = "user-1", email = "user@example.com", password = "encoded-password")

        val domain = mapper.toDomain(dto)

        assertEquals("user-1", domain.id)
        assertEquals("user@example.com", domain.email)
        assertEquals("encoded-password", domain.password)
    }

    @Test
    fun `toDto preserves id, email and password`() {
        val domain = User(id = "user-1", email = "user@example.com", password = "encoded-password")

        val dto = mapper.toDto(domain)

        assertEquals("user-1", dto.id)
        assertEquals("user@example.com", dto.email)
        assertEquals("encoded-password", dto.password)
    }
}
