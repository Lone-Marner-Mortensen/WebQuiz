package quiz.repository.jpa.adapters

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import quiz.repository.dto.QuizCompletionDto

interface QuizCompletionEntityRepository : JpaRepository<QuizCompletionDto, String> {
    fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageable: Pageable): Page<QuizCompletionDto>
}
