package quiz.domain.repository

import quiz.domain.response.PagedResult
import quiz.domain.model.QuizCompletion

interface QuizCompletionRepository {
    fun save(completion: QuizCompletion): QuizCompletion
    fun existsByQuizIdAndUserId(quizId: String, userId: String): Boolean
    fun findByUserIdOrderByCompletedAtDesc(userId: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion>
}
