package quiz.domain.service

interface UserService {
    fun registerUser(email: String, rawPassword: String)
}
