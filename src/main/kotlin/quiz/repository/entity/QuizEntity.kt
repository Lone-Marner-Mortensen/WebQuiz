package quiz.repository.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.OffsetDateTime

@Entity
@Table(name = "quizzes")
class QuizEntity(
    @Id
    val id: String,

    @Column(nullable = false)
    val title: String,

    @Column(name = "author_id", nullable = false)
    val authorId: String,

    @OneToMany(mappedBy = "quiz", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderColumn(name = "question_order")
    @Fetch(value = FetchMode.SUBSELECT)
    var questions: List<QuestionEntity> = emptyList(),

    @Column(nullable = false)
    val createdAt: OffsetDateTime
)
