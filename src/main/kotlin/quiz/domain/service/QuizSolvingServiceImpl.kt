package quiz.domain.service

import org.springframework.stereotype.Service
import quiz.domain.Clock
import quiz.domain.IdGenerator
import quiz.domain.exception.InvalidAnswerException
import quiz.domain.exception.QuizNotFoundException
import quiz.domain.model.QuizCompletion
import quiz.domain.response.AnswerResult
import quiz.domain.response.PagedResult
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository

@Service
class QuizSolvingServiceImpl(
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val quizRepository: QuizRepository,
    private val quizCompletionRepository: QuizCompletionRepository
) : QuizSolvingService {

    override fun solveQuiz(id: String, answers: List<Int>, userEmail: String): AnswerResult {
        val quiz = quizRepository.findById(id) ?: throw QuizNotFoundException("No quiz with id: $id")
        if (answers.size != quiz.questions.size) {
            throw InvalidAnswerException(
                "Expected ${quiz.questions.size} answer(s), got ${answers.size}"
            )
        }
        // Since the quizzes are small, it's okay to require all answers to be correct.
        val allCorrect = quiz.questions.indices.all { index -> answers[index] == quiz.questions[index].answer }
        if (!allCorrect) {
            return AnswerResult(success = false, feedback = "Wrong answer(s)! Please, try again.")
        }
        if (quizCompletionRepository.existsByQuizIdAndUserId(id, userEmail)) {
            return AnswerResult(success = true, feedback = "Congratulations, you're right!")
        }
        quizCompletionRepository.save(
            QuizCompletion(
                id = idGenerator.createId(),
                quizId = id,
                userId = userEmail,
                completedAt = clock.now()
            )
        )
        return AnswerResult(success = true, feedback = "Congratulations, you're right!")
    }

    override fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion> =
        quizCompletionRepository.findByUserIdOrderByCompletedAtDesc(userEmail, pageNumber, pageSize)
}
