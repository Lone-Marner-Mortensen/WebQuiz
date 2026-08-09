package quiz.repository.dto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.OffsetDateTime

@Entity
@Table(name = "quiz_completions")
class QuizCompletionDto(
    @Id
    val id: String,

    @Column(name = "quiz_id", nullable = false)
    val quizId: String,

    @Column(nullable = false)
    val userEmail: String,

    @Column(nullable = false)
    val completedAt: OffsetDateTime,

    // Added delete cascade so that when a Quiz get's deleted
    // a QuizCompletion will also be deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false, insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val quiz: QuizDto? = null
)
