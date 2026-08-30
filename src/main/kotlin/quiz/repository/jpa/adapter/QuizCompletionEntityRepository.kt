package quiz.repository.jpa.adapter

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import quiz.repository.entity.QuizCompletionEntity

interface QuizCompletionEntityRepository : JpaRepository<QuizCompletionEntity, String> {
    fun existsByQuizIdAndUserId(quizId: String, userId: String): Boolean
    fun findByUserIdOrderByCompletedAtDesc(userId: String, pageable: Pageable): Page<QuizCompletionEntity>
}
