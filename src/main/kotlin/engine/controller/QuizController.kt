package engine.controller

import engine.quiz.dto.AnswerResponse
import engine.quiz.dto.CompletionResponse
import engine.quiz.dto.CreateQuizRequest
import engine.quiz.dto.QuizResponse
import engine.quiz.dto.SolveQuizRequest
import engine.service.DeleteResult
import engine.service.QuizService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val PAGE_SIZE = 10

@RestController
class QuizController(private val quizService: QuizService) {

    @PostMapping("/api/quizzes")
    fun createQuiz(
        @Valid @RequestBody request: CreateQuizRequest,
        authentication: Authentication
    ): QuizResponse {
        return quizService.createQuiz(request, authentication.name)
    }

    @GetMapping("/api/quizzes/{id}")
    fun getQuiz(@PathVariable id: Int): ResponseEntity<QuizResponse> {
        return quizService.getQuiz(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/api/quizzes")
    fun getAllQuizzes(@RequestParam(defaultValue = "0") page: Int): Page<QuizResponse> {
        return quizService.getAllQuizzes(PageRequest.of(page, PAGE_SIZE))
    }

    @GetMapping("/api/quizzes/completed")
    fun getCompletedQuizzes(
        @RequestParam(defaultValue = "0") page: Int,
        authentication: Authentication
    ): Page<CompletionResponse> {
        return quizService.getCompletions(authentication.name, PageRequest.of(page, PAGE_SIZE))
    }

    @PostMapping("/api/quizzes/{id}/solve")
    fun solveQuiz(
        @PathVariable id: Int,
        @RequestBody request: SolveQuizRequest,
        authentication: Authentication
    ): ResponseEntity<AnswerResponse> {
        return quizService.solveQuiz(id, request.answer, authentication.name)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @DeleteMapping("/api/quizzes/{id}")
    fun deleteQuiz(@PathVariable id: Int, authentication: Authentication): ResponseEntity<Void> {
        return when (quizService.deleteQuiz(id, authentication.name)) {
            DeleteResult.Deleted -> ResponseEntity.noContent().build()
            DeleteResult.NotFound -> ResponseEntity.notFound().build()
            DeleteResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }
}
