package quiz.domain.service

import quiz.domain.model.AnswerResult
import quiz.domain.model.PagedResult
import quiz.domain.model.QuizCompletion

interface QuizSolvingService {
    fun solveQuiz(id: String, answers: List<Int>, userEmail: String): AnswerResult
    fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion>
}
