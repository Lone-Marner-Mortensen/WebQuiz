package quiz.controller

import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import quiz.controller.dto.AnswerResultDto
import quiz.controller.dto.QuizCompletionResponseDto
import quiz.controller.dto.CreateQuizRequestDto
import quiz.controller.dto.QuizResponseDto
import quiz.controller.dto.SolveQuizRequestDto
import quiz.controller.mapper.AnswerMapper
import quiz.controller.mapper.CompletionMapper
import quiz.controller.mapper.QuizMapper
import quiz.controller.mapper.toPage
import quiz.domain.service.QuestionDraft
import quiz.domain.service.QuizService

private const val PAGE_SIZE = 10

@RestController
class QuizController(
    private val quizService: QuizService,
    private val quizMapper: QuizMapper,
    private val completionMapper: CompletionMapper,
    private val answerMapper: AnswerMapper
) {

    @PostMapping("/api/quizzes")
    fun createQuiz(
        @Valid @RequestBody request: CreateQuizRequestDto,
        authentication: Authentication
    ): QuizResponseDto {
        val quiz = quizService.createQuiz(
            title = request.title ?: "",
            author = authentication.name,
            questions = (request.questions ?: emptyList()).map {
                QuestionDraft(
                    text = it.text ?: "",
                    options = it.options ?: emptyList(),
                    answer = it.answer ?: -1
                )
            }
        )
        return quizMapper.toResponseDto(quiz)
    }

    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Quiz found"),
        ApiResponse(responseCode = "404", description = "No quiz with this id")
    )
    @GetMapping("/api/quizzes/{id}")
    fun getQuiz(@PathVariable id: String): QuizResponseDto {
        return quizMapper.toResponseDto(quizService.getQuiz(id))
    }

    @GetMapping("/api/quizzes")
    fun getAllQuizzes(@RequestParam(defaultValue = "0") page: Int): Page<QuizResponseDto> {
        return quizService.getAllQuizzes(page, PAGE_SIZE).toPage().map { quizMapper.toResponseDto(it) }
    }

    @GetMapping("/api/quizzes/completed")
    fun getCompletedQuizzes(
        @RequestParam(defaultValue = "0") page: Int,
        authentication: Authentication
    ): Page<QuizCompletionResponseDto> {
        return quizService.getCompletions(authentication.name, page, PAGE_SIZE)
            .toPage()
            .map { completionMapper.toResponseDto(it) }
    }

    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Answer checked"),
        ApiResponse(responseCode = "404", description = "No quiz with this id")
    )
    @PostMapping("/api/quizzes/{id}/solve")
    fun solveQuiz(
        @PathVariable id: String,
        @RequestBody request: SolveQuizRequestDto,
        authentication: Authentication
    ): AnswerResultDto {
        return answerMapper.toResponseDto(quizService.solveQuiz(id, request.answers, authentication.name))
    }

    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Quiz deleted"),
        ApiResponse(responseCode = "403", description = "Requester is not the quiz author"),
        ApiResponse(responseCode = "404", description = "No quiz with this id")
    )
    @DeleteMapping("/api/quizzes/{id}")
    fun deleteQuiz(@PathVariable id: String, authentication: Authentication): ResponseEntity<Void> {
        quizService.deleteQuiz(id, authentication.name)
        return ResponseEntity.noContent().build()
    }
}
