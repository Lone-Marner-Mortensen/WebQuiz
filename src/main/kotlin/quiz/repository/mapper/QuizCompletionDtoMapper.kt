package quiz.repository.mapper

import org.mapstruct.Mapper
import quiz.domain.model.QuizCompletion
import quiz.repository.entity.QuizCompletionEntity

// `toDto` is hand-written and not MapStruct-generated. `QuizCompletionEntity` is a JPA entity with a
// Hibernate-required synthetic no-arg constructor (from kotlin-jpa); MapStruct picks that constructor over
// the real one and leaves every field null, since `QuizCompletionEntity` has no setters to fill them in afterward.
@Mapper(componentModel = "spring")
interface QuizCompletionDtoMapper {
    fun toDomain(dto: QuizCompletionEntity): QuizCompletion

    fun toDto(domain: QuizCompletion): QuizCompletionEntity =
        QuizCompletionEntity(
            id = domain.id,
            quizId = domain.quizId,
            userId = domain.userId,
            completedAt = domain.completedAt
        )
}
