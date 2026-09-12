package quiz.domain.service

import org.springframework.stereotype.Service
import quiz.domain.repository.Clock
import quiz.domain.repository.IdGenerator
import quiz.domain.exception.InvalidQuizException
import quiz.domain.exception.QuizAuthorMismatchException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.model.QuestionDraft
import quiz.domain.model.Quiz
import quiz.domain.model.Question
import quiz.domain.model.PagedResult
import quiz.domain.repository.QuizRepository

@Service
class QuizManagementServiceImpl(
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val quizRepository: QuizRepository
) : QuizManagementService {

    override fun createQuiz(title: String, author: String, questions: List<QuestionDraft>): Quiz {
        if (questions.isEmpty()) {
            throw InvalidQuizException("Quiz must have at least one question")
        }
        questions.forEachIndexed { index, question ->
            if (question.options.size < 2) {
                throw InvalidQuizException(
                    "Question $index must have at least 2 options"
                )
            }
            if (question.answer !in question.options.indices) {
                throw InvalidQuizException(
                    "Question $index answer index ${question.answer} is out of range for ${question.options.size} option(s)"
                )
            }
        }
        return quizRepository.save(
            Quiz(
                id = idGenerator.createId(),
                title = title,
                authorId = author,
                questions = questions.map { Question(text = it.text, options = it.options, answer = it.answer) },
                createdAt = clock.now()
            )
        )
    }

    override fun getQuiz(id: String): Quiz =
        quizRepository.findById(id) ?: throw QuizNotFoundException("No quiz with id: $id")

    override fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz> =
        quizRepository.findAll(pageNumber, pageSize)

    override fun deleteQuiz(id: String, requesterEmail: String) {
        val quiz = quizRepository.findById(id) ?: throw QuizNotFoundException("No quiz with id: $id")
        if (quiz.authorId != requesterEmail) {
            throw QuizAuthorMismatchException("Requester is not the quiz author")
        }
        quizRepository.deleteById(id)
    }
}
