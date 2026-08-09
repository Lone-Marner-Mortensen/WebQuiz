package quiz.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import quiz.domain.exception.DuplicateEmailException
import quiz.domain.exception.InvalidAnswerException
import quiz.domain.exception.QuizAuthorMismatchException
import quiz.domain.exception.QuizNotFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage ?: "Invalid value"}" }
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Malformed request body on {} {}", request.method, request.requestURI, ex)
        return buildError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body could not be parsed")
    }

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(ex: DuplicateEmailException): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", ex.message ?: "Email already exists")
    }

    @ExceptionHandler(QuizNotFoundException::class)
    fun handleQuizNotFound(ex: QuizNotFoundException): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", ex.message ?: "Quiz not found")
    }

    @ExceptionHandler(InvalidAnswerException::class)
    fun handleInvalidAnswer(ex: InvalidAnswerException): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", ex.message ?: "Invalid answer")
    }

    @ExceptionHandler(QuizAuthorMismatchException::class)
    fun handleQuizAuthorMismatch(ex: QuizAuthorMismatchException): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.FORBIDDEN, "QUIZ_AUTHOR_MISMATCH", ex.message ?: "Requester is not the quiz author")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid argument on {} {}", request.method, request.requestURI, ex)
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request")
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Data integrity violation on {} {}", request.method, request.requestURI, ex)
        return buildError(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "Data integrity violation")
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception on {} {}", request.method, request.requestURI, ex)
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred")
    }

    private fun buildError(
        status: HttpStatus,
        errorCode: String,
        message: String
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(status)
            .body(ErrorResponse(status.value(), errorCode, message))
    }
}
