package quiz.domain.repository

import quiz.domain.response.PagedResult
import quiz.domain.QuizCompletion

interface QuizCompletionRepository {
    fun save(completion: QuizCompletion): QuizCompletion
    fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion>
}
