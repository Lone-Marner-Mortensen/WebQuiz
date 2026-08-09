package quiz.repository

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import quiz.domain.response.PagedResult
import quiz.domain.Quiz
import quiz.domain.repository.QuizRepository
import quiz.repository.jpa.adapters.QuizEntityRepository
import quiz.repository.mapper.QuizDtoMapper
import quiz.repository.mapper.toPagedResult

@Repository
class QuizRepositoryImpl(
    private val jpaRepository: QuizEntityRepository,
    private val mapper: QuizDtoMapper
) : QuizRepository {

    override fun save(quiz: Quiz): Quiz {
        return mapper.toDomain(jpaRepository.save(mapper.toDto(quiz)))
    }

    override fun findById(id: String): Quiz? {
        return jpaRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)
    }

    override fun findAll(pageNumber: Int, pageSize: Int): PagedResult<Quiz> {
        return jpaRepository.findAllByOrderByCreatedAt(PageRequest.of(pageNumber, pageSize))
            .map { mapper.toDomain(it) }
            .toPagedResult()
    }

    override fun deleteById(id: String) {
        jpaRepository.deleteById(id)
    }
}
