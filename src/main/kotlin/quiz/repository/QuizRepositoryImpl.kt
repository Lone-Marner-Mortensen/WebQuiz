package quiz.repository

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import quiz.domain.model.PagedResult
import quiz.domain.model.Quiz
import quiz.domain.repository.QuizRepository
import quiz.repository.jpa.adapter.QuizEntityRepository
import quiz.repository.mapper.QuizDtoMapper
import quiz.repository.mapper.toPagedResult

@Repository
class QuizRepositoryImpl(
    private val jpaRepository: QuizEntityRepository,
    private val mapper: QuizDtoMapper
) : QuizRepository {

    override fun save(quiz: Quiz): Quiz =
        mapper.toDto(quiz)
            .let(jpaRepository::save)
            .let(mapper::toDomain)

    // `@Transactional` keeps the Hibernate session open while `mapper.toDomain` walks the lazy
    // `questions` collection; without it, the session (and thus the collection) is closed by the
    // time the mapping runs, since `jpaRepository.findById`/`findAllByOrderByCreatedAtDesc` each
    // open and close their own transaction.
    @Transactional(readOnly = true)
    override fun findById(id: String): Quiz? =
        jpaRepository.findById(id)
            .map(mapper::toDomain)
            .orElse(null)

    @Transactional(readOnly = true)
    override fun findAll(pageNumber: Int, pageSize: Int): PagedResult<Quiz> =
        jpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(pageNumber, pageSize))
            .map(mapper::toDomain)
            .toPagedResult()

    override fun deleteById(id: String) =
        jpaRepository.deleteById(id)
}
