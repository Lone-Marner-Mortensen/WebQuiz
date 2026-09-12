package quiz.domain.repository

import quiz.domain.model.PagedResult
import quiz.domain.model.Quiz

interface QuizRepository {
    fun save(quiz: Quiz): Quiz
    fun findById(id: String): Quiz?
    fun findAll(pageNumber: Int, pageSize: Int): PagedResult<Quiz>
    fun deleteById(id: String)
}
