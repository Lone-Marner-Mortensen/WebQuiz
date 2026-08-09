package quiz.repository

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import quiz.domain.response.PagedResult
import quiz.domain.QuizCompletion
import quiz.domain.repository.QuizCompletionRepository
import quiz.repository.jpa.adapters.QuizCompletionEntityRepository
import quiz.repository.mapper.QuizCompletionDtoMapper
import quiz.repository.mapper.toPagedResult

@Repository
class QuizCompletionRepositoryImpl(
    private val jpaRepository: QuizCompletionEntityRepository,
    private val mapper: QuizCompletionDtoMapper
) : QuizCompletionRepository {

    override fun save(completion: QuizCompletion): QuizCompletion {
        return mapper.toDomain(jpaRepository.save(mapper.toDto(completion)))
    }

    override fun findByUserEmailOrderByCompletedAtDesc(
        userEmail: String,
        pageNumber: Int,
        pageSize: Int
    ): PagedResult<QuizCompletion> {
        return jpaRepository.findByUserEmailOrderByCompletedAtDesc(userEmail, PageRequest.of(pageNumber, pageSize))
            .map { mapper.toDomain(it) }
            .toPagedResult()
    }
}
