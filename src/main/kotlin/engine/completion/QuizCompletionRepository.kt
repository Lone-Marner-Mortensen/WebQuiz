package engine.completion

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface QuizCompletionRepository : JpaRepository<QuizCompletion, Int> {
    fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageable: Pageable): Page<QuizCompletion>
}
