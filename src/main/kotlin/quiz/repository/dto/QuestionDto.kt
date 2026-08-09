package quiz.repository.dto

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode

@Entity
@Table(name = "questions")
class QuestionDto(
    @Id
    val id: String,

    @Column(nullable = false)
    val text: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = [JoinColumn(name = "question_id")])
    @Column(name = "option_value", nullable = false)
    @Fetch(value = FetchMode.SUBSELECT)
    val options: List<String>,

    @Column(nullable = false)
    val answer: Int,

    @ManyToOne
    @JoinColumn(name = "quiz_id")
    var quiz: QuizDto?
)
