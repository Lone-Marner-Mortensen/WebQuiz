# User Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user registration and HTTP Basic auth/authorization to the Web Quiz Engine so all quiz operations require a registered user, and users can only delete their own quizzes.

**Architecture:** New `engine.user` package (User entity, repository, register DTO, service, `UserDetailsService`) plugs into a new stateless Spring Security config using HTTP Basic + BCrypt. `Quiz` gains an `author` (email) column stamped at creation; a new delete flow checks ownership. A shared `@RestControllerAdvice` handles validation and duplicate-email errors.

**Tech Stack:** Kotlin 2.2.20, Spring Boot 4.1.0, Spring Security (new dependency), Spring Data JPA, H2, Gradle 9.5.1 (wrapper), JDK 21 toolchain.

## Global Constraints

- Do not change existing response shapes for `GET /api/quizzes`, `GET /api/quizzes/{id}`, `POST /api/quizzes/{id}/solve` — no `author` or `answer` leakage into `QuizResponse`.
- Existing create-quiz validation (title/text non-blank, options size ≥ 2 → 400 otherwise) must keep working unchanged.
- `POST /actuator/shutdown` must remain accessible without authentication.
- Passwords must never be stored in plaintext — BCrypt via Spring Security's `PasswordEncoder`.
- Email format validation: must contain `@` and `.` (use `jakarta.validation.constraints.Email`).
- Password validation: minimum 5 characters.
- No automated JUnit tests beyond the existing `contextLoads` — this project is graded by an external Hyperskill test suite; verification is manual via `curl` (per approved spec `docs/superpowers/specs/2026-07-28-user-authorization-design.md`).
- Project root: `/Users/lonemarnermortensen/IdeaProjects/WebQuizKotlin`. Not a git repo — skip git add/commit steps; use `./gradlew build -q` and `./gradlew bootRun` for verification instead.

---

### Task 1: User entity, repository, and register DTO

**Files:**
- Create: `src/main/kotlin/engine/user/User.kt`
- Create: `src/main/kotlin/engine/user/UserRepository.kt`
- Create: `src/main/kotlin/engine/user/dto/RegisterRequest.kt`

**Interfaces:**
- Consumes: nothing (new package).
- Produces:
  - `User(id: Int? = null, email: String = "", password: String = "")` — JPA entity, table `users`.
  - `UserRepository : JpaRepository<User, Int>` with `fun findByEmail(email: String): User?` and `fun existsByEmail(email: String): Boolean`.
  - `RegisterRequest(email: String?, password: String?)` data class with validation annotations.

- [ ] **Step 1: Create the `User` entity**

```kotlin
package engine.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false)
    var password: String = ""
)
```

- [ ] **Step 2: Create the `UserRepository`**

```kotlin
package engine.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Int> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}
```

- [ ] **Step 3: Create the `RegisterRequest` DTO**

```kotlin
package engine.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String?,

    @field:NotBlank
    @field:Size(min = 5, message = "Password must be at least 5 characters long")
    val password: String?
)
```

- [ ] **Step 4: Compile check**

Run: `./gradlew compileKotlin -q`
Expected: build succeeds with no errors.

---

### Task 2: DuplicateEmailException, UserService, and global exception handler

**Files:**
- Create: `src/main/kotlin/engine/user/DuplicateEmailException.kt`
- Create: `src/main/kotlin/engine/user/UserService.kt`
- Create: `src/main/kotlin/engine/GlobalExceptionHandler.kt`
- Modify: `src/main/kotlin/engine/controller/QuizController.kt:1-50` (remove the inline `@ExceptionHandler` — it moves to `GlobalExceptionHandler`)

**Interfaces:**
- Consumes: `User`, `UserRepository` from Task 1.
- Produces:
  - `DuplicateEmailException(message: String)` — `RuntimeException` subclass.
  - `UserService.registerUser(request: RegisterRequest): Unit` — throws `DuplicateEmailException` if email taken.
  - `GlobalExceptionHandler` (`@RestControllerAdvice`) handling `MethodArgumentNotValidException` and `DuplicateEmailException`, both returning 400 with the exception's message as body.

- [ ] **Step 1: Create `DuplicateEmailException`**

```kotlin
package engine.user

class DuplicateEmailException(message: String) : RuntimeException(message)
```

- [ ] **Step 2: Create `UserService`**

```kotlin
package engine.user

import engine.user.dto.RegisterRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun registerUser(request: RegisterRequest) {
        val email = request.email!!
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("Email already taken: $email")
        }
        val user = User(
            email = email,
            password = passwordEncoder.encode(request.password!!)
        )
        userRepository.save(user)
    }
}
```

Note: `PasswordEncoder` is defined in Task 4's `SecurityConfig` — this file will not compile until Task 4 adds that bean. That's expected; Task 2's compile check below only verifies syntax of the files it touches in isolation via the IDE, not a full build. The full build is verified at the end of Task 4.

- [ ] **Step 3: Create `GlobalExceptionHandler`**

```kotlin
package engine

import engine.user.DuplicateEmailException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(ex: DuplicateEmailException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.BAD_REQUEST)
    }
}
```

- [ ] **Step 4: Remove the inline exception handler from `QuizController`**

In `src/main/kotlin/engine/controller/QuizController.kt`, delete these lines (and now-unused imports `HttpStatus`, `MethodArgumentNotValidException`, `ExceptionHandler`):

```kotlin
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.BAD_REQUEST)
    }
```

The resulting file's imports should be exactly:

```kotlin
package engine.controller

import engine.quiz.dto.AnswerResponse
import engine.quiz.dto.CreateQuizRequest
import engine.quiz.dto.QuizResponse
import engine.quiz.dto.SolveQuizRequest
import engine.service.QuizService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
```

(Full deletion/edit of this controller's delete-quiz endpoint and author-passing happens in Task 5 — for this task, only remove the exception handler block and unused imports; leave the rest of the file as-is.)

- [ ] **Step 5: Note on compile check**

Do not run a full build yet — `UserService` depends on a `PasswordEncoder` bean that doesn't exist until Task 4. Proceed to Task 3.

---

### Task 3: Add Spring Security dependency, UserDetailsService, and security config

**Files:**
- Modify: `build.gradle:22-30` (add Spring Security starter dependency)
- Create: `src/main/kotlin/engine/user/AppUserDetailsService.kt`
- Create: `src/main/kotlin/engine/SecurityConfig.kt`

**Interfaces:**
- Consumes: `UserRepository` from Task 1, `PasswordEncoder` (defined in this task).
- Produces:
  - `AppUserDetailsService : UserDetailsService` with `loadUserByUsername(email: String): UserDetails`.
  - `SecurityConfig` with beans: `PasswordEncoder`, `AuthenticationProvider`, `SecurityFilterChain`.

- [ ] **Step 1: Add the Spring Security starter to `build.gradle`**

In the `dependencies { ... }` block, add this line (any position among the other `implementation` lines):

```groovy
    implementation 'org.springframework.boot:spring-boot-starter-security'
```

The full `dependencies` block should read:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.jetbrains.kotlin:kotlin-reflect'
    runtimeOnly 'com.h2database:h2'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 2: Create `AppUserDetailsService`**

```kotlin
package engine.user

import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AppUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("No user found with email: $email")
        return SpringUser.builder()
            .username(user.email)
            .password(user.password)
            .authorities(emptyList())
            .build()
    }
}
```

- [ ] **Step 3: Create `SecurityConfig`**

```kotlin
package engine

import engine.user.AppUserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(private val userDetailsService: AppUserDetailsService) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        val provider = DaoAuthenticationProvider(passwordEncoder())
        provider.setUserDetailsService(userDetailsService)
        return provider
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/register", "/actuator/shutdown").permitAll()
                it.anyRequest().authenticated()
            }
            .httpBasic { }
        return http.build()
    }
}
```

- [ ] **Step 4: Compile check**

Run: `./gradlew compileKotlin -q`
Expected: build succeeds with no errors (this resolves the `PasswordEncoder` dependency `UserService` needed).

---

### Task 4: Register endpoint

**Files:**
- Create: `src/main/kotlin/engine/controller/UserController.kt`

**Interfaces:**
- Consumes: `UserService.registerUser(request: RegisterRequest)` from Task 2, `RegisterRequest` from Task 1.
- Produces: `POST /api/register`.

- [ ] **Step 1: Create `UserController`**

```kotlin
package engine.controller

import engine.user.UserService
import engine.user.dto.RegisterRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(private val userService: UserService) {

    @PostMapping("/api/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Void> {
        userService.registerUser(request)
        return ResponseEntity.ok().build()
    }
}
```

- [ ] **Step 2: Full build check**

Run: `./gradlew build -q`
Expected: build succeeds with no errors.

- [ ] **Step 3: Manual verification — register flow**

Remove any stale dev database first, since the schema is changing in later tasks anyway:

```bash
rm -f /Users/lonemarnermortensen/IdeaProjects/quizdb.mv.db
```

Start the app in the background:

```bash
cd /Users/lonemarnermortensen/IdeaProjects/WebQuizKotlin && ./gradlew bootRun > /tmp/webquiz.log 2>&1 &
sleep 15
```

Verify registration:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@mail.org","password":"strongpassword"}'
# expect 200

curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@mail.org","password":"strongpassword"}'
# expect 400 (duplicate)

curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" \
  -d '{"email":"not-an-email","password":"strongpassword"}'
# expect 400 (invalid email)

curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test2@mail.org","password":"123"}'
# expect 400 (short password)

curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8891/api/quizzes
# expect 401 (no auth yet — quizzes endpoint should already be locked down from Task 3's SecurityConfig)
```

Stop the app:

```bash
curl -s -X POST http://localhost:8891/actuator/shutdown
```

Confirm all expected status codes matched before proceeding.

---

### Task 5: Quiz author field and authenticated create/get/list/solve

**Files:**
- Modify: `src/main/kotlin/engine/quiz/Quiz.kt` (add `author` column)
- Modify: `src/main/kotlin/engine/service/QuizService.kt` (accept author on create)
- Modify: `src/main/kotlin/engine/controller/QuizController.kt` (pass authenticated email to `createQuiz`)

**Interfaces:**
- Consumes: `Authentication` injected by Spring Security (available on every request past Task 3's filter chain).
- Produces: `QuizService.createQuiz(request: CreateQuizRequest, author: String): QuizResponse` (signature change — `author` param added).

- [ ] **Step 1: Add `author` to the `Quiz` entity**

In `src/main/kotlin/engine/quiz/Quiz.kt`, add a new column after `id`:

```kotlin
    @Column(nullable = false)
    var author: String = "",
```

Full updated entity:

```kotlin
package engine.quiz

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode

@Entity
@Table(name = "quizzes")
data class Quiz(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var text: String = "",

    @Column(nullable = false)
    var author: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_options", joinColumns = [JoinColumn(name = "quiz_id")])
    @Column(name = "option_value", nullable = false)
    @Fetch(value = FetchMode.SUBSELECT)
    var options: MutableList<String> = mutableListOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_answers", joinColumns = [JoinColumn(name = "quiz_id")])
    @Column(name = "answer_value", nullable = false)
    @Fetch(value = FetchMode.SUBSELECT)
    var answer: MutableList<Int> = mutableListOf()
)
```

- [ ] **Step 2: Update `QuizService.createQuiz` to accept and stamp `author`**

In `src/main/kotlin/engine/service/QuizService.kt`, change the `createQuiz` method:

```kotlin
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
```

(`toResponse()` stays unchanged — `QuizResponse` has no `author` field, so it never leaks into API responses.)

- [ ] **Step 3: Update `QuizController.createQuiz` to pass the authenticated email**

In `src/main/kotlin/engine/controller/QuizController.kt`, change the create endpoint and add the `Authentication` import:

```kotlin
import org.springframework.security.core.Authentication
```

```kotlin
    @PostMapping("/api/quizzes")
    fun createQuiz(
        @Valid @RequestBody request: CreateQuizRequest,
        authentication: Authentication
    ): QuizResponse {
        return quizService.createQuiz(request, authentication.name)
    }
```

- [ ] **Step 4: Full build check**

Run: `./gradlew build -q`
Expected: build succeeds with no errors.

---

### Task 6: Delete quiz endpoint with ownership check

**Files:**
- Modify: `src/main/kotlin/engine/service/QuizService.kt` (add `deleteQuiz`)
- Modify: `src/main/kotlin/engine/controller/QuizController.kt` (add `DELETE /api/quizzes/{id}`)

**Interfaces:**
- Consumes: `Quiz.author` from Task 5, `Authentication.name` from Spring Security.
- Produces: `QuizService.DeleteResult` sealed result type, `QuizService.deleteQuiz(id: Int, requesterEmail: String): DeleteResult`, `DELETE /api/quizzes/{id}` → 204/403/404.

- [ ] **Step 1: Add a `DeleteResult` sealed class and `deleteQuiz` to `QuizService`**

In `src/main/kotlin/engine/service/QuizService.kt`, add the sealed class at file scope (outside the `QuizService` class) and the method inside the class:

```kotlin
sealed class DeleteResult {
    object Deleted : DeleteResult()
    object NotFound : DeleteResult()
    object Forbidden : DeleteResult()
}
```

```kotlin
    fun deleteQuiz(id: Int, requesterEmail: String): DeleteResult {
        val quiz = quizRepository.findById(id).orElse(null) ?: return DeleteResult.NotFound
        if (quiz.author != requesterEmail) {
            return DeleteResult.Forbidden
        }
        quizRepository.delete(quiz)
        return DeleteResult.Deleted
    }
```

Full updated `QuizService.kt`:

```kotlin
package engine.service

import engine.quiz.Quiz
import engine.quiz.dto.AnswerResponse
import engine.quiz.dto.CreateQuizRequest
import engine.quiz.dto.QuizResponse
import engine.repository.QuizRepository
import org.springframework.stereotype.Service

sealed class DeleteResult {
    object Deleted : DeleteResult()
    object NotFound : DeleteResult()
    object Forbidden : DeleteResult()
}

@Service
class QuizService(private val quizRepository: QuizRepository) {

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

    fun getAllQuizzes(): List<QuizResponse> {
        return quizRepository.findAll().map { it.toResponse() }
    }

    fun solveQuiz(id: Int, answerList: List<Int>): AnswerResponse? {
        val quiz = quizRepository.findById(id).orElse(null) ?: return null
        return if (answerList.sorted() == quiz.answer.sorted()) {
            AnswerResponse(success = true, feedback = "Congratulations, you're right!")
        } else {
            AnswerResponse(success = false, feedback = "Wrong answer! Please, try again.")
        }
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
```

- [ ] **Step 2: Add the `DELETE` endpoint to `QuizController`**

Add this import:

```kotlin
import engine.service.DeleteResult
import org.springframework.web.bind.annotation.DeleteMapping
```

Add this method to the `QuizController` class:

```kotlin
    @DeleteMapping("/api/quizzes/{id}")
    fun deleteQuiz(@PathVariable id: Int, authentication: Authentication): ResponseEntity<Void> {
        return when (quizService.deleteQuiz(id, authentication.name)) {
            DeleteResult.Deleted -> ResponseEntity.noContent().build()
            DeleteResult.NotFound -> ResponseEntity.notFound().build()
            DeleteResult.Forbidden -> ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }
```

Add the `HttpStatus` import (removed in Task 2, needed again here):

```kotlin
import org.springframework.http.HttpStatus
```

Full updated `QuizController.kt`:

```kotlin
package engine.controller

import engine.quiz.dto.AnswerResponse
import engine.quiz.dto.CreateQuizRequest
import engine.quiz.dto.QuizResponse
import engine.quiz.dto.SolveQuizRequest
import engine.service.DeleteResult
import engine.service.QuizService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

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
    fun getAllQuizzes(): List<QuizResponse> {
        return quizService.getAllQuizzes()
    }

    @PostMapping("/api/quizzes/{id}/solve")
    fun solveQuiz(@PathVariable id: Int, @RequestBody request: SolveQuizRequest): ResponseEntity<AnswerResponse> {
        return quizService.solveQuiz(id, request.answer)
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
```

- [ ] **Step 3: Full build check**

Run: `./gradlew build -q`
Expected: build succeeds with no errors.

---

### Task 7: End-to-end manual verification

**Files:** none (verification only).

- [ ] **Step 1: Reset the dev database**

```bash
rm -f /Users/lonemarnermortensen/IdeaProjects/quizdb.mv.db
```

- [ ] **Step 2: Start the app**

```bash
cd /Users/lonemarnermortensen/IdeaProjects/WebQuizKotlin && ./gradlew bootRun > /tmp/webquiz.log 2>&1 &
sleep 15
```

- [ ] **Step 3: Register two users**

```bash
curl -s -o /dev/null -w "register userA: %{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" -d '{"email":"userA@mail.org","password":"strongpassword"}'
# expect 200

curl -s -o /dev/null -w "register userB: %{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" -d '{"email":"userB@mail.org","password":"strongpassword"}'
# expect 200
```

- [ ] **Step 4: Confirm unauthenticated access is rejected**

```bash
curl -s -o /dev/null -w "unauthenticated list: %{http_code}\n" http://localhost:8891/api/quizzes
# expect 401
```

- [ ] **Step 5: Create a quiz as userA and verify response shape**

```bash
curl -s -u userA@mail.org:strongpassword -X POST http://localhost:8891/api/quizzes \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Quiz","text":"What is 2+2?","options":["3","4","5"],"answer":[1]}'
# expect 200 with JSON body containing id, title, text, options — NO author, NO answer field
```

Note the returned `id` (call it `QUIZ_ID`) for the next steps.

- [ ] **Step 6: Verify existing validation still returns 400**

```bash
curl -s -o /dev/null -w "missing title: %{http_code}\n" -u userA@mail.org:strongpassword \
  -X POST http://localhost:8891/api/quizzes \
  -H "Content-Type: application/json" \
  -d '{"title":"","text":"x","options":["a","b"]}'
# expect 400

curl -s -o /dev/null -w "too few options: %{http_code}\n" -u userA@mail.org:strongpassword \
  -X POST http://localhost:8891/api/quizzes \
  -H "Content-Type: application/json" \
  -d '{"title":"t","text":"x","options":["a"]}'
# expect 400
```

- [ ] **Step 7: Verify get/list/solve work authenticated**

```bash
curl -s -o /dev/null -w "get quiz: %{http_code}\n" -u userA@mail.org:strongpassword http://localhost:8891/api/quizzes/QUIZ_ID
# expect 200 (replace QUIZ_ID with the real id)

curl -s -o /dev/null -w "list quizzes: %{http_code}\n" -u userA@mail.org:strongpassword http://localhost:8891/api/quizzes
# expect 200

curl -s -u userA@mail.org:strongpassword -X POST http://localhost:8891/api/quizzes/QUIZ_ID/solve \
  -H "Content-Type: application/json" -d '{"answer":[1]}'
# expect 200 with {"success":true,...}
```

- [ ] **Step 8: Verify delete authorization — wrong user forbidden**

```bash
curl -s -o /dev/null -w "delete as wrong user: %{http_code}\n" -u userB@mail.org:strongpassword \
  -X DELETE http://localhost:8891/api/quizzes/QUIZ_ID
# expect 403
```

- [ ] **Step 9: Verify delete authorization — nonexistent quiz**

```bash
curl -s -o /dev/null -w "delete nonexistent: %{http_code}\n" -u userA@mail.org:strongpassword \
  -X DELETE http://localhost:8891/api/quizzes/999999
# expect 404
```

- [ ] **Step 10: Verify delete authorization — correct owner succeeds**

```bash
curl -s -o /dev/null -w "delete as owner: %{http_code}\n" -u userA@mail.org:strongpassword \
  -X DELETE http://localhost:8891/api/quizzes/QUIZ_ID
# expect 204
```

- [ ] **Step 11: Confirm shutdown remains open**

```bash
curl -s -o /dev/null -w "shutdown: %{http_code}\n" -X POST http://localhost:8891/actuator/shutdown
# expect 200, and the app process should exit
```

- [ ] **Step 12: Review all status codes above against expectations**

Every line printed by Steps 3–11 must match its `# expect` comment. If any mismatch, stop and debug before considering this plan complete — do not proceed to declare the feature done.

---

## Self-Review Notes

- **Spec coverage:** register (Task 4), auth on all quiz ops (Task 3 SecurityConfig + Task 5/6 controller changes), delete with ownership (Task 6), BCrypt (Task 3), unchanged create-quiz validation (untouched in Task 5/6), unchanged response shapes (verified explicitly in Task 7 Step 5), shutdown open (Task 3 + verified Task 7 Step 11). All spec sections have a corresponding task.
- **Type consistency:** `QuizService.createQuiz` signature (`request, author`) matches between Task 5 (definition) and Task 5 Step 3 (`QuizController` call site). `DeleteResult` sealed class and its three objects (`Deleted`, `NotFound`, `Forbidden`) are used identically in Task 6's service and controller code. `Authentication.name` used consistently in Task 5 and Task 6 controller methods.
- **No placeholders:** all steps contain full, exact code.
