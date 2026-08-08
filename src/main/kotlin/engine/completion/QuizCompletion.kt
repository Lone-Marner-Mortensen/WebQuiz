package engine.completion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "quiz_completions")
data class QuizCompletion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false)
    var quizId: Int = 0,

    @Column(nullable = false)
    var userEmail: String = "",

    @Column(nullable = false)
    var completedAt: OffsetDateTime = OffsetDateTime.now()
)
