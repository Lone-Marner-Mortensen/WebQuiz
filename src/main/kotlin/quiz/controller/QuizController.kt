package quiz.controller

import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import quiz.controller.dto.AnswerResultDto
import quiz.controller.dto.QuizCompletionResponseDto
import quiz.controller.dto.CreateQuizRequestDto
import quiz.controller.dto.CreateQuizResponseDto
import quiz.controller.dto.SolveQuizRequestDto
import quiz.controller.mapper.AnswerMapper
import quiz.controller.mapper.CompletionMapper
import quiz.controller.mapper.QuizMapper
import quiz.controller.mapper.toPage
import quiz.domain.repository.UserRepository
import quiz.domain.service.QuestionDraft
import quiz.domain.service.QuizManagementService
import quiz.domain.service.QuizSolvingService

private const val PAGE_SIZE = 10

@RestController
@RequestMapping("/api/quizzes")
class QuizController(
    private val quizManagementService: QuizManagementService,
    private val quizSolvingService: QuizSolvingService,
    private val quizMapper: QuizMapper,
    private val completionMapper: CompletionMapper,
    private val answerMapper: AnswerMapper,
    private val userRepository: UserRepository
) {

    private fun userIdFor(authentication: Authentication): String =
        userRepository.findByEmail(authentication.name)
            ?.id
            ?: throw UsernameNotFoundException("User not found: ${authentication.name}")

    @PostMapping
    fun createQuiz(
        @Valid @RequestBody request: CreateQuizRequestDto,
        authentication: Authentication
    ): CreateQuizResponseDto {
        val quiz = quizManagementService.createQuiz(
            title = request.title,
            author = userIdFor(authentication),
            questions = (request.questions ?: emptyList()).map {
                QuestionDraft(
                    text = it.text,
                    options = it.options,
                    answer = it.answer
                )
            }
        )
        return quizMapper.toResponseDto(quiz)
    }

    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Quiz found"),
        ApiResponse(responseCode = "404", description = "No quiz with this id")
    )
    @GetMapping("/{id}")
    fun getQuiz(@PathVariable id: String): CreateQuizResponseDto =
        quizMapper.toResponseDto(quizManagementService.getQuiz(id))

    @GetMapping
    fun getAllQuizzes(@RequestParam(defaultValue = "0") page: Int): Page<CreateQuizResponseDto> =
        quizManagementService.getAllQuizzes(page, PAGE_SIZE).toPage().map { quizMapper.toResponseDto(it) }

    @GetMapping("/completed")
    fun getCompletedQuizzes(
        @RequestParam(defaultValue = "0") page: Int,
        authentication: Authentication
    ): Page<QuizCompletionResponseDto> {
        return quizSolvingService.getCompletions(userIdFor(authentication), page, PAGE_SIZE)
            .toPage()
            .map { completionMapper.toResponseDto(it) }
    }

    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Answer checked"),
        ApiResponse(responseCode = "404", description = "No quiz with this id")
    )
    @PostMapping("/{id}/solve")
    fun solveQuiz(
        @PathVariable id: String,
        @RequestBody request: SolveQuizRequestDto,
        authentication: Authentication
    ): AnswerResultDto =
        answerMapper.toResponseDto(quizSolvingService.solveQuiz(id, request.answers, userIdFor(authentication)))

    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Quiz deleted"),
        ApiResponse(responseCode = "403", description = "Requester is not the quiz author"),
        ApiResponse(responseCode = "404", description = "No quiz with this id")
    )
    @DeleteMapping("/{id}")
    fun deleteQuiz(@PathVariable id: String, authentication: Authentication): ResponseEntity<Void> {
        quizManagementService.deleteQuiz(id, userIdFor(authentication))
        return ResponseEntity.noContent().build()
    }
}
