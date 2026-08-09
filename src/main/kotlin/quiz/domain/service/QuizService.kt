package quiz.domain.service

import quiz.domain.response.AnswerResult
import quiz.domain.response.PagedResult
import quiz.domain.Quiz
import quiz.domain.QuizCompletion

interface QuizService {
    fun createQuiz(title: String, author: String, questions: List<QuestionDraft>): Quiz
    fun getQuiz(id: String): Quiz
    fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz>
    fun solveQuiz(id: String, answers: List<Int>, userEmail: String): AnswerResult
    fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion>
    fun deleteQuiz(id: String, requesterEmail: String)
}
