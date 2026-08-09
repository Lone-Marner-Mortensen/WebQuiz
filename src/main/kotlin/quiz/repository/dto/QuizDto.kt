package quiz.repository.dto

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.OffsetDateTime

@Entity
@Table(name = "quizzes")
class QuizDto(
    @Id
    val id: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val author: String,

    @OneToMany(mappedBy = "quiz", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "question_order")
    @Fetch(value = FetchMode.SUBSELECT)
    val questions: List<QuestionDto>,

    @Column(nullable = false)
    val createdAt: OffsetDateTime
)
