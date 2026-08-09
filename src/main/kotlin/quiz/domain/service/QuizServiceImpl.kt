package quiz.domain.service

import org.springframework.stereotype.Service
import quiz.domain.exception.InvalidAnswerException
import quiz.domain.exception.QuizAuthorMismatchException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.response.AnswerResult
import quiz.domain.response.PagedResult
import quiz.domain.Question
import quiz.domain.Quiz
import quiz.domain.QuizCompletion
import quiz.domain.createId
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository
import java.time.OffsetDateTime

@Service
class QuizServiceImpl(
    private val quizRepository: QuizRepository,
    private val quizCompletionRepository: QuizCompletionRepository
) : QuizService {

    override fun createQuiz(title: String, author: String, questions: List<QuestionDraft>): Quiz {
        if (questions.isEmpty()) {
            throw InvalidAnswerException("Quiz must have at least one question")
        }
        questions.forEachIndexed { index, question ->
            if (question.options.size < 2) {
                throw InvalidAnswerException(
                    "Question $index must have at least 2 options"
                )
            }
            if (question.answer !in question.options.indices) {
                throw InvalidAnswerException(
                    "Question $index answer index ${question.answer} is out of range for ${question.options.size} option(s)"
                )
            }
        }
        return quizRepository.save(
            Quiz(
                id = createId(),
                title = title,
                author = author,
                questions = questions.map { Question(text = it.text, options = it.options, answer = it.answer) }
            )
        )
    }

    override fun getQuiz(id: String): Quiz {
        return quizRepository.findById(id) ?: throw QuizNotFoundException("No quiz with id: $id")
    }

    override fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz> {
        return quizRepository.findAll(pageNumber, pageSize)
    }

    override fun solveQuiz(id: String, answers: List<Int>, userEmail: String): AnswerResult {
        val quiz = quizRepository.findById(id) ?: throw QuizNotFoundException("No quiz with id: $id")
        if (answers.size != quiz.questions.size) {
            throw InvalidAnswerException(
                "Expected ${quiz.questions.size} answer(s), got ${answers.size}"
            )
        }
        val allCorrect = quiz.questions.indices.all { index -> answers[index] == quiz.questions[index].answer }
        return if (allCorrect) {
            quizCompletionRepository.save(
                QuizCompletion(
                    id = createId(),
                    quizId = id,
                    userEmail = userEmail,
                    completedAt = OffsetDateTime.now()
                )
            )
            AnswerResult(success = true, feedback = "Congratulations, you're right!")
        } else {
            AnswerResult(success = false, feedback = "Wrong answer(s)! Please, try again.")
        }
    }

    override fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion> {
        return quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(userEmail, pageNumber, pageSize)
    }

    override fun deleteQuiz(id: String, requesterEmail: String) {
        val quiz = quizRepository.findById(id) ?: throw QuizNotFoundException("No quiz with id: $id")
        if (quiz.author != requesterEmail) {
            throw QuizAuthorMismatchException("Requester is not the quiz author")
        }
        quizRepository.deleteById(id)
    }
}
