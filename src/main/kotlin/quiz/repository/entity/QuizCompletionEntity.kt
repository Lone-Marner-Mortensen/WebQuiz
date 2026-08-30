package quiz.repository.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "quiz_completions")
class QuizCompletionEntity(
    @Id
    val id: String,

    @Column(name = "quiz_id", nullable = false)
    val quizId: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(nullable = false)
    val completedAt: OffsetDateTime,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false, insertable = false, updatable = false)
    val quiz: QuizEntity? = null
)
