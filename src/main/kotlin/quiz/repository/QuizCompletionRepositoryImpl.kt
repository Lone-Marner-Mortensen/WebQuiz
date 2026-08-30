package quiz.repository

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import quiz.domain.response.PagedResult
import quiz.domain.model.QuizCompletion
import quiz.domain.repository.QuizCompletionRepository
import quiz.repository.jpa.adapter.QuizCompletionEntityRepository
import quiz.repository.mapper.QuizCompletionDtoMapper
import quiz.repository.mapper.toPagedResult

@Repository
class QuizCompletionRepositoryImpl(
    private val jpaRepository: QuizCompletionEntityRepository,
    private val mapper: QuizCompletionDtoMapper
) : QuizCompletionRepository {

    override fun save(completion: QuizCompletion): QuizCompletion =
        mapper.toDto(completion)
            .let(jpaRepository::save)
            .let(mapper::toDomain)

    override fun existsByQuizIdAndUserId(quizId: String, userId: String): Boolean =
        jpaRepository.existsByQuizIdAndUserId(quizId, userId)

    override fun findByUserIdOrderByCompletedAtDesc(
        userId: String,
        pageNumber: Int,
        pageSize: Int
    ): PagedResult<QuizCompletion> {
        return jpaRepository.findByUserIdOrderByCompletedAtDesc(userId, PageRequest.of(pageNumber, pageSize))
            .map { mapper.toDomain(it) }
            .toPagedResult()
    }
}
