package engine.quiz

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode

@Entity
@Table(name = "quizzes")
data class Quiz(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var text: String = "",

    @Column(nullable = false)
    var author: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_options", joinColumns = [JoinColumn(name = "quiz_id")])
    @Column(name = "option_value", nullable = false)
    @Fetch(value = FetchMode.SUBSELECT)
    var options: MutableList<String> = mutableListOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_answers", joinColumns = [JoinColumn(name = "quiz_id")])
    @Column(name = "answer_value", nullable = false)
    @Fetch(value = FetchMode.SUBSELECT)
    var answer: MutableList<Int> = mutableListOf()
)
