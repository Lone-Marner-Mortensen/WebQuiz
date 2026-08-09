package quiz.repository.jpa.adapters

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import quiz.repository.dto.QuizDto

interface QuizEntityRepository : JpaRepository<QuizDto, String> {
    fun findAllByOrderByCreatedAt(pageable: Pageable): Page<QuizDto>
}
