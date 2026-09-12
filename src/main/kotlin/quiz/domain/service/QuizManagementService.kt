package quiz.domain.service

import quiz.domain.model.PagedResult
import quiz.domain.model.QuestionDraft
import quiz.domain.model.Quiz

interface QuizManagementService {
    fun createQuiz(title: String, author: String, questions: List<QuestionDraft>): Quiz
    fun getQuiz(id: String): Quiz
    fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz>
    fun deleteQuiz(id: String, requesterEmail: String)
}
