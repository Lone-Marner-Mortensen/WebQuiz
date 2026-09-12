package quiz.infrastructure.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User

class AuthenticatedUser(
    val id: String,
    username: String,
    password: String,
    authorities: Collection<GrantedAuthority> = emptyList()
) : User(username, password, authorities)
