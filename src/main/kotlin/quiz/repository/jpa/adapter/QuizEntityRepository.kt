package quiz.repository.jpa.adapter

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import quiz.repository.entity.QuizEntity

interface QuizEntityRepository : JpaRepository<QuizEntity, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<QuizEntity>
}
