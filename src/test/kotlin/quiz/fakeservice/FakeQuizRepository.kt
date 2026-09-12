package quiz.fakeservice

import quiz.domain.model.Quiz
import quiz.domain.repository.QuizRepository
import quiz.domain.model.PagedResult

class FakeQuizRepository : QuizRepository {
    private val quizzesById = mutableMapOf<String, Quiz>()

    override fun save(quiz: Quiz): Quiz {
        quizzesById[quiz.id] = quiz
        return quiz
    }

    override fun findById(id: String): Quiz? = quizzesById[id]

    override fun findAll(pageNumber: Int, pageSize: Int): PagedResult<Quiz> {
        val sorted = quizzesById.values.sortedByDescending { it.createdAt }
        val content = sorted.drop(pageNumber * pageSize).take(pageSize)
        return PagedResult(
            content = content,
            pageNumber = pageNumber,
            pageSize = pageSize,
            totalElements = sorted.size.toLong(),
            totalPages = if (sorted.isEmpty()) 0 else (sorted.size + pageSize - 1) / pageSize
        )
    }

    override fun deleteById(id: String) {
        quizzesById.remove(id)
    }
}
