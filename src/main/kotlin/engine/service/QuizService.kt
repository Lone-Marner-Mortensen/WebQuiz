package engine.service

import engine.completion.QuizCompletion
import engine.completion.QuizCompletionRepository
import engine.quiz.Quiz
import engine.quiz.dto.AnswerResponse
import engine.quiz.dto.CompletionResponse
import engine.quiz.dto.CreateQuizRequest
import engine.quiz.dto.QuizResponse
import engine.repository.QuizRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

sealed class DeleteResult {
    object Deleted : DeleteResult()
    object NotFound : DeleteResult()
    object Forbidden : DeleteResult()
}

@Service
class QuizService(
    private val quizRepository: QuizRepository,
    private val quizCompletionRepository: QuizCompletionRepository
) {

    fun createQuiz(request: CreateQuizRequest, author: String): QuizResponse {
        val quiz = Quiz(
            title = request.title!!,
            text = request.text!!,
            author = author,
            options = request.options!!.toMutableList(),
            answer = (request.answer ?: emptyList()).toMutableList()
        )
        val savedQuiz = quizRepository.save(quiz)
        return savedQuiz.toResponse()
    }

    fun getQuiz(id: Int): QuizResponse? {
        return quizRepository.findById(id).orElse(null)?.toResponse()
    }

    fun getAllQuizzes(pageable: Pageable): Page<QuizResponse> {
        return quizRepository.findAll(pageable).map { it.toResponse() }
    }

    fun solveQuiz(id: Int, answerList: List<Int>, userEmail: String): AnswerResponse? {
        val quiz = quizRepository.findById(id).orElse(null) ?: return null
        return if (answerList.sorted() == quiz.answer.sorted()) {
            quizCompletionRepository.save(
                QuizCompletion(quizId = id, userEmail = userEmail, completedAt = OffsetDateTime.now())
            )
            AnswerResponse(success = true, feedback = "Congratulations, you're right!")
        } else {
            AnswerResponse(success = false, feedback = "Wrong answer! Please, try again.")
        }
    }

    fun getCompletions(userEmail: String, pageable: Pageable): Page<CompletionResponse> {
        return quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(userEmail, pageable)
            .map { CompletionResponse(id = it.quizId, completedAt = it.completedAt) }
    }

    fun deleteQuiz(id: Int, requesterEmail: String): DeleteResult {
        val quiz = quizRepository.findById(id).orElse(null) ?: return DeleteResult.NotFound
        if (quiz.author != requesterEmail) {
            return DeleteResult.Forbidden
        }
        quizRepository.delete(quiz)
        return DeleteResult.Deleted
    }

    private fun Quiz.toResponse(): QuizResponse {
        return QuizResponse(
            id = id ?: 0,
            title = title,
            text = text,
            options = options
        )
    }
}
