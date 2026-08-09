# Role-Based Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `ADMIN` role to the existing quiz app so that quiz browsing/solving becomes public, quiz creation/completions-listing requires login, quiz deletion allows the author OR an admin, and a new admin-only endpoint can promote a registered user to admin.

**Architecture:** A new `Role` enum flows from `User` domain → JPA entity → Spring Security authorities (`ROLE_ADMIN`/`ROLE_USER`), so `SecurityConfig`'s filter chain can use `hasRole("ADMIN")` for URL-shape rules. The per-resource "author or admin" decision for quiz deletion cannot be expressed as a URL matcher, so it lives in `QuizServiceImpl`, which gains a `UserRepository` dependency to look up the requester's role. A startup `ApplicationRunner` seeds one bootstrap admin from env vars; a new admin-only endpoint promotes further users.

**Tech Stack:** Kotlin, Spring Boot 4.1 (Web, Security, Data JPA, Validation, Actuator), MapStruct 1.6.3 (kapt), H2, JUnit 5 + MockK + Spring Boot Test.

**Spec:** `docs/superpowers/specs/2026-08-24-role-based-authorization-design.md`

## Global Constraints

- Exactly two roles: `USER`, `ADMIN`. No custom authorities beyond `ROLE_USER`/`ROLE_ADMIN`.
- No demotion endpoint, no user-listing endpoint, no audit log — out of scope.
- HTTP Basic remains the authentication mechanism — do not change it.
- No frontend/UI work.
- Self-registration (`POST /api/register`) must always produce `Role.USER`; there is no path to admin through registration.
- Target access matrix (from spec):
  - `GET /api/quizzes` → public
  - `GET /api/quizzes/{id}` → public
  - `POST /api/quizzes/{id}/solve` → public
  - `GET /api/quizzes/completed` → authenticated (any role)
  - `POST /api/quizzes` → authenticated (any role)
  - `DELETE /api/quizzes/{id}` → authenticated; author OR `ADMIN`
  - `POST /api/admin/**` → `ADMIN` only
  - `POST /api/register`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/shutdown` → public (unchanged)

---

## Task 1: Test infrastructure — isolate tests from the dev H2 file

The dev datasource is a file-based H2 DB (`jdbc:h2:file:../webquizdb`). Running `@SpringBootTest` against it risks file locks against a running dev server and leaves test data behind. Add a test-only properties override using an in-memory DB before any test that boots the Spring context is added.

**Files:**
- Create: `src/test/resources/application.properties`
- Test: `src/test/kotlin/quiz/webquiz/WebQuizApplicationTests.kt` (existing, used to verify)

**Interfaces:**
- Produces: a Spring Boot test classpath that resolves `spring.datasource.url` to an in-memory H2 instance, isolated per test run.

- [ ] **Step 1: Create the test properties override**

```properties
spring.application.name=WebQuiz
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
admin.email=${ADMIN_EMAIL:admin@example.com}
admin.password=${ADMIN_PASSWORD:changeit123}
```

Spring Boot Test resolves `src/test/resources/application.properties` ahead of `src/main/resources/application.properties` on the test classpath, so this fully overrides the datasource for all tests without touching the main file.

- [ ] **Step 2: Run the existing context-load test to confirm isolation works**

Run: `./gradlew test --tests "quiz.webquiz.WebQuizApplicationTests"`
Expected: BUILD SUCCESSFUL, test passes using the in-memory DB (no file lock errors, no `../webquizdb` file created/modified by the test run).

- [ ] **Step 3: Commit**

```bash
git add src/test/resources/application.properties
git commit -m "test: isolate Spring Boot tests with in-memory H2"
```

---

## Task 2: `Role` enum + `role` field on `User` and `UserDto`

**Files:**
- Create: `src/main/kotlin/quiz/domain/Role.kt`
- Modify: `src/main/kotlin/quiz/domain/User.kt`
- Modify: `src/main/kotlin/quiz/repository/dto/UserDto.kt`
- Test: `src/test/kotlin/quiz/repository/mapper/UserDtoMapperTest.kt`

**Interfaces:**
- Produces: `quiz.domain.Role` enum with values `USER`, `ADMIN`; `User.role: Role` (default `Role.USER`); `UserDto.role: String` (mutable).
- Consumes: `quiz.repository.mapper.UserDtoMapperImpl` — the MapStruct-generated implementation class (compiled by kapt into the same package as the `UserDtoMapper` interface), used directly in the test with no Spring context.

- [ ] **Step 1: Write the failing test**

```kotlin
package quiz.repository.mapper

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import quiz.domain.Role
import quiz.domain.User
import quiz.repository.dto.UserDto

class UserDtoMapperTest {

    private val mapper: UserDtoMapper = UserDtoMapperImpl()

    @Test
    fun `toDto maps ADMIN role to its string name`() {
        val user = User(id = "1", email = "admin@example.com", password = "hash", role = Role.ADMIN)

        val dto = mapper.toDto(user)

        assertEquals("ADMIN", dto.role)
    }

    @Test
    fun `toDomain maps role string back to enum`() {
        val dto = UserDto(id = "1", email = "user@example.com", password = "hash", role = "USER")

        val domain = mapper.toDomain(dto)

        assertEquals(Role.USER, domain.role)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.repository.mapper.UserDtoMapperTest"`
Expected: FAIL — compile error, `Role` doesn't exist yet and `User`/`UserDto` constructors don't accept `role`.

- [ ] **Step 3: Create the `Role` enum**

```kotlin
package quiz.domain

enum class Role {
    USER, ADMIN
}
```

- [ ] **Step 4: Add `role` to `User`**

```kotlin
package quiz.domain

data class User(
    val id: String,
    val email: String,
    val password: String,
    val role: Role = Role.USER
)
```

- [ ] **Step 5: Add `role` to `UserDto`**

```kotlin
package quiz.repository.dto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserDto(
    @Id
    val id: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false)
    var role: String
)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.repository.mapper.UserDtoMapperTest"`
Expected: PASS. (Kapt regenerates `UserDtoMapperImpl` on compile since MapStruct auto-maps same-named `Role`/`String` fields via `.name`/`valueOf`.)

- [ ] **Step 7: Fix call sites broken by the new required `UserDto` constructor param**

Run: `./gradlew compileKotlin` and read the errors — `UserServiceImpl` constructs `User(...)` (Task 5 will touch this; for now just confirm it still compiles because `User.role` has a default). Confirm `UserDto` has no other direct construction sites outside the mapper.

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (no other file directly constructs `UserDto`).

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/quiz/domain/Role.kt src/main/kotlin/quiz/domain/User.kt src/main/kotlin/quiz/repository/dto/UserDto.kt src/test/kotlin/quiz/repository/mapper/UserDtoMapperTest.kt
git commit -m "feat: add Role enum and role field to User/UserDto"
```

---

## Task 3: Bridge `role` into Spring Security authorities

**Files:**
- Modify: `src/main/kotlin/quiz/security/UserDetailsServiceAdapter.kt`
- Test: `src/test/kotlin/quiz/security/UserDetailsServiceAdapterTest.kt`

**Interfaces:**
- Consumes: `quiz.domain.User(id, email, password, role)` (Task 2), `quiz.domain.repository.UserRepository.findByEmail(email): User?`.
- Produces: `UserDetailsServiceAdapter.loadUserByUsername(username)` returns a `UserDetails` whose `authorities` contains exactly one `SimpleGrantedAuthority("ROLE_<role.name>")`.

- [ ] **Step 1: Write the failing test**

```kotlin
package quiz.security

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UsernameNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.repository.UserRepository

class UserDetailsServiceAdapterTest {

    private val userRepository: UserRepository = mockk()
    private val adapter = UserDetailsServiceAdapter(userRepository)

    @Test
    fun `admin user gets ROLE_ADMIN authority`() {
        every { userRepository.findByEmail("admin@example.com") } returns
            User(id = "1", email = "admin@example.com", password = "hash", role = Role.ADMIN)

        val details = adapter.loadUserByUsername("admin@example.com")

        assertEquals(setOf("ROLE_ADMIN"), details.authorities.map { it.authority }.toSet())
    }

    @Test
    fun `regular user gets ROLE_USER authority`() {
        every { userRepository.findByEmail("user@example.com") } returns
            User(id = "2", email = "user@example.com", password = "hash", role = Role.USER)

        val details = adapter.loadUserByUsername("user@example.com")

        assertEquals(setOf("ROLE_USER"), details.authorities.map { it.authority }.toSet())
    }

    @Test
    fun `unknown user throws UsernameNotFoundException`() {
        every { userRepository.findByEmail("ghost@example.com") } returns null

        assertFailsWith<UsernameNotFoundException> {
            adapter.loadUserByUsername("ghost@example.com")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.security.UserDetailsServiceAdapterTest"`
Expected: FAIL on the first two tests — `details.authorities` is empty, not `{ROLE_ADMIN}`/`{ROLE_USER}`.

- [ ] **Step 3: Update `UserDetailsServiceAdapter`**

```kotlin
package quiz.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import quiz.domain.repository.UserRepository

@Service
class UserDetailsServiceAdapter(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("User not found: $username")
        return SpringUser.builder()
            .username(user.email)
            .password(user.password)
            .authorities(listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")))
            .build()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.security.UserDetailsServiceAdapterTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/quiz/security/UserDetailsServiceAdapter.kt src/test/kotlin/quiz/security/UserDetailsServiceAdapterTest.kt
git commit -m "feat: bridge user role into Spring Security authorities"
```

---

## Task 4: Update the security filter chain access matrix

**Files:**
- Modify: `src/main/kotlin/quiz/security/SecurityConfig.kt`
- Test: `src/test/kotlin/quiz/security/SecurityConfigIntegrationTest.kt`

**Interfaces:**
- Consumes: `UserDetailsServiceAdapter` (Task 3, unchanged signature), `quiz.domain.repository.UserRepository.save(User): User` (existing), `PasswordEncoder.encode(String): String` (existing bean from `SecurityConfig`).
- Produces: an HTTP-level access matrix matching the Global Constraints table. This task does not yet add `/api/admin/**` reachable behavior (no controller exists there yet — Task 7 adds it) but the matcher rule for it is added now since it's part of `SecurityConfig`.

- [ ] **Step 1: Write the failing test**

```kotlin
package quiz.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail("matrix-user@example.com")) {
            userRepository.save(
                User(
                    id = createId(),
                    email = "matrix-user@example.com",
                    password = passwordEncoder.encode("password123"),
                    role = Role.USER
                )
            )
        }
    }

    @Test
    fun `GET quizzes list is public`() {
        val response = restTemplate.getForEntity("/api/quizzes", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `GET single quiz by unknown id is public (404, not 401)`() {
        val response = restTemplate.getForEntity("/api/quizzes/does-not-exist", String::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST solve is public (404 for unknown id, not 401)`() {
        val response = restTemplate.postForEntity("/api/quizzes/does-not-exist/solve", "{\"answer\":[]}", String::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST create quiz without auth is rejected`() {
        val response = restTemplate.postForEntity("/api/quizzes", "{}", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `POST create quiz with auth is not rejected for auth reasons`() {
        val response = restTemplate
            .withBasicAuth("matrix-user@example.com", "password123")
            .postForEntity("/api/quizzes", "{}", String::class.java)
        // Body is empty/invalid so it will fail validation (400), but must NOT be 401/403.
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `GET completed quizzes without auth is rejected`() {
        val response = restTemplate.getForEntity("/api/quizzes/completed", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.security.SecurityConfigIntegrationTest"`
Expected: FAIL — currently `GET /api/quizzes`, `GET /api/quizzes/{id}`, and `POST /api/quizzes/{id}/solve` all return 401 instead of 200/404, since everything but `/api/register` and docs currently requires authentication.

- [ ] **Step 3: Update `SecurityConfig`**

```kotlin
package quiz.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
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
class SecurityConfig(private val userDetailsService: UserDetailsServiceAdapter) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/api/quizzes", "/api/quizzes/*").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/quizzes/*/solve").permitAll()
                it.requestMatchers(
                    "/api/register",
                    "/actuator/shutdown",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .httpBasic { }
        return http.build()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.security.SecurityConfigIntegrationTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/quiz/security/SecurityConfig.kt src/test/kotlin/quiz/security/SecurityConfigIntegrationTest.kt
git commit -m "feat: rework security filter chain access matrix"
```

---

## Task 5: Author-or-admin authorization for quiz deletion

**Files:**
- Modify: `src/main/kotlin/quiz/domain/service/QuizServiceImpl.kt`
- Test: `src/test/kotlin/quiz/domain/service/QuizServiceImplTest.kt`

**Interfaces:**
- Consumes: `quiz.domain.repository.UserRepository.findByEmail(email): User?` (existing interface), `quiz.domain.repository.QuizRepository` (existing, unchanged), `quiz.domain.repository.QuizCompletionRepository` (existing, unchanged).
- Produces: `QuizServiceImpl(quizRepository, quizCompletionRepository, userRepository)` — constructor now takes 3 params (was 2). `deleteQuiz(id, requesterEmail)` returns `DeleteResult.Deleted` for the author or any `Role.ADMIN` user, `DeleteResult.Forbidden` otherwise, `DeleteResult.NotFound` if the quiz doesn't exist.

- [ ] **Step 1: Write the failing test**

```kotlin
package quiz.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import quiz.domain.Quiz
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import quiz.domain.response.DeleteResult

class QuizServiceImplTest {

    private val quizRepository: QuizRepository = mockk()
    private val quizCompletionRepository: QuizCompletionRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val service = QuizServiceImpl(quizRepository, quizCompletionRepository, userRepository)

    private val quiz = Quiz(
        id = "quiz-1",
        title = "t",
        text = "x",
        author = "author@example.com",
        options = listOf("a", "b"),
        answer = listOf(0)
    )

    @Test
    fun `author can delete their own quiz`() {
        every { quizRepository.findById("quiz-1") } returns quiz
        every { userRepository.findByEmail("author@example.com") } returns
            User(id = "1", email = "author@example.com", password = "hash", role = Role.USER)
        every { quizRepository.deleteById("quiz-1") } returns Unit

        val result = service.deleteQuiz("quiz-1", "author@example.com")

        assertEquals(DeleteResult.Deleted, result)
        verify { quizRepository.deleteById("quiz-1") }
    }

    @Test
    fun `admin can delete someone else's quiz`() {
        every { quizRepository.findById("quiz-1") } returns quiz
        every { userRepository.findByEmail("admin@example.com") } returns
            User(id = "2", email = "admin@example.com", password = "hash", role = Role.ADMIN)
        every { quizRepository.deleteById("quiz-1") } returns Unit

        val result = service.deleteQuiz("quiz-1", "admin@example.com")

        assertEquals(DeleteResult.Deleted, result)
        verify { quizRepository.deleteById("quiz-1") }
    }

    @Test
    fun `non-author non-admin is forbidden`() {
        every { quizRepository.findById("quiz-1") } returns quiz
        every { userRepository.findByEmail("other@example.com") } returns
            User(id = "3", email = "other@example.com", password = "hash", role = Role.USER)

        val result = service.deleteQuiz("quiz-1", "other@example.com")

        assertEquals(DeleteResult.Forbidden, result)
    }

    @Test
    fun `unknown quiz returns NotFound`() {
        every { quizRepository.findById("missing") } returns null

        val result = service.deleteQuiz("missing", "anyone@example.com")

        assertEquals(DeleteResult.NotFound, result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.domain.service.QuizServiceImplTest"`
Expected: FAIL — compile error, `QuizServiceImpl` constructor currently takes only 2 params.

- [ ] **Step 3: Update `QuizServiceImpl`**

```kotlin
package quiz.domain.service

import org.springframework.stereotype.Service
import quiz.domain.response.AnswerResult
import quiz.domain.response.DeleteResult
import quiz.domain.response.PagedResult
import quiz.domain.Quiz
import quiz.domain.QuizCompletion
import quiz.domain.Role
import quiz.domain.createId
import quiz.domain.repository.QuizCompletionRepository
import quiz.domain.repository.QuizRepository
import quiz.domain.repository.UserRepository
import java.time.OffsetDateTime

@Service
class QuizServiceImpl(
    private val quizRepository: QuizRepository,
    private val quizCompletionRepository: QuizCompletionRepository,
    private val userRepository: UserRepository
) : QuizService {

    override fun createQuiz(title: String, text: String, options: List<String>, answer: List<Int>, author: String): Quiz {
        return quizRepository.save(
            Quiz(
                id = createId(),
                title = title,
                text = text,
                author = author,
                options = options,
                answer = answer
            )
        )
    }

    override fun getQuiz(id: String): Quiz? {
        return quizRepository.findById(id)
    }

    override fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz> {
        return quizRepository.findAll(pageNumber, pageSize)
    }

    override fun solveQuiz(id: String, answer: List<Int>, userEmail: String): AnswerResult? {
        val quiz = quizRepository.findById(id) ?: return null
        return if (answer.sorted() == quiz.answer.sorted()) {
            quizCompletionRepository.save(
                QuizCompletion(
                    id = createId(),
                    quizId = id,
                    userEmail = userEmail,
                    completedAt = OffsetDateTime.now()
                )
            )
            AnswerResult(success = true, feedback = "Congratulations, you're right!")
        } else {
            AnswerResult(success = false, feedback = "Wrong answer! Please, try again.")
        }
    }

    override fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion> {
        return quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(userEmail, pageNumber, pageSize)
    }

    override fun deleteQuiz(id: String, requesterEmail: String): DeleteResult {
        val quiz = quizRepository.findById(id) ?: return DeleteResult.NotFound
        val requester = userRepository.findByEmail(requesterEmail)
        val isAdmin = requester?.role == Role.ADMIN
        if (quiz.author != requesterEmail && !isAdmin) {
            return DeleteResult.Forbidden
        }
        quizRepository.deleteById(id)
        return DeleteResult.Deleted
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.domain.service.QuizServiceImplTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 5: Run the full test suite to catch any other broken callers**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. (No other production code constructs `QuizServiceImpl` directly — Spring wires it via constructor injection, and `UserRepository` is already a registered bean via `UserRepositoryImpl`.)

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/quiz/domain/service/QuizServiceImpl.kt src/test/kotlin/quiz/domain/service/QuizServiceImplTest.kt
git commit -m "feat: allow admins to delete any quiz, not just their own"
```

---

## Task 6: Registration always assigns `Role.USER`

**Files:**
- Modify: `src/main/kotlin/quiz/domain/service/UserServiceImpl.kt` (explicit role assignment; behavior is already correct via `User`'s default, this task makes it explicit and adds a regression test)
- Test: `src/test/kotlin/quiz/domain/service/UserServiceImplTest.kt`

**Interfaces:**
- Consumes: `quiz.domain.repository.UserRepository` (existing), `org.springframework.security.crypto.password.PasswordEncoder` (existing bean).
- Produces: no interface change — `UserServiceImpl.registerUser(email, rawPassword)` still returns `Unit`; behavior now explicitly pinned by test.

- [ ] **Step 1: Write the failing test**

```kotlin
package quiz.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.repository.UserRepository

class UserServiceImplTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val service = UserServiceImpl(userRepository, passwordEncoder)

    @Test
    fun `registerUser always creates a USER role, never ADMIN`() {
        every { userRepository.existsByEmail("new@example.com") } returns false
        every { passwordEncoder.encode("password123") } returns "hashed"
        val savedUser = slot<User>()
        every { userRepository.save(capture(savedUser)) } answers { savedUser.captured }

        service.registerUser("new@example.com", "password123")

        assertEquals(Role.USER, savedUser.captured.role)
        verify { userRepository.save(any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.domain.service.UserServiceImplTest"`
Expected: FAIL if `User`'s default role isn't being asserted anywhere yet — run it first; since `User.role` already defaults to `Role.USER` from Task 2, this may already pass. Proceed to Step 3 regardless to make the intent explicit in the source.

- [ ] **Step 3: Make role assignment explicit in `UserServiceImpl`**

```kotlin
package quiz.domain.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import quiz.domain.exception.DuplicateEmailException
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserService {

    override fun registerUser(email: String, rawPassword: String) {
        if (userRepository.existsByEmail(email)) {
            throw DuplicateEmailException("Email already taken: $email")
        }
        userRepository.save(
            User(
                id = createId(),
                email = email,
                password = passwordEncoder.encode(rawPassword) ?: "",
                role = Role.USER
            )
        )
    }
}
```

**Note:** at the time this task runs, `UserServiceImpl.kt` already imports `quiz.domain.exception.DuplicateEmailException` (a package rename landed ahead of this plan) — the snippet above reflects that current import path, not the one in the design spec.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.domain.service.UserServiceImplTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/quiz/domain/service/UserServiceImpl.kt src/test/kotlin/quiz/domain/service/UserServiceImplTest.kt
git commit -m "test: pin registration to always assign Role.USER"
```

---

## Task 7: Bootstrap admin account on startup

**Files:**
- Create: `src/main/kotlin/quiz/security/AdminBootstrapper.kt`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/kotlin/quiz/security/AdminBootstrapperTest.kt`

**Interfaces:**
- Consumes: `quiz.domain.repository.UserRepository` (existing: `existsByEmail`, `save`), `org.springframework.security.crypto.password.PasswordEncoder.encode(String): String` (existing bean), `quiz.domain.createId()` (existing).
- Produces: `AdminBootstrapper(adminEmail: String, adminPassword: String, userRepository: UserRepository, passwordEncoder: PasswordEncoder)` implementing `ApplicationRunner`, with `run(args: ApplicationArguments)` creating a `Role.ADMIN` user if `adminEmail` doesn't already exist.

- [ ] **Step 1: Add properties for the bootstrap admin**

Append to `src/main/resources/application.properties`:

```properties
admin.email=${ADMIN_EMAIL:admin@example.com}
admin.password=${ADMIN_PASSWORD:changeit123}
```

(`src/test/resources/application.properties` from Task 1 already has matching defaults, so tests booting the full context won't fail on missing properties.)

- [ ] **Step 2: Write the failing test**

```kotlin
package quiz.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.repository.UserRepository

class AdminBootstrapperTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val args: ApplicationArguments = mockk()

    @Test
    fun `creates admin when none exists`() {
        every { userRepository.existsByEmail("admin@example.com") } returns false
        every { passwordEncoder.encode("changeit123") } returns "hashed"
        every { userRepository.save(any()) } answers { firstArg() }

        val bootstrapper = AdminBootstrapper("admin@example.com", "changeit123", userRepository, passwordEncoder)
        bootstrapper.run(args)

        verify {
            userRepository.save(match { it.email == "admin@example.com" && it.role == Role.ADMIN && it.password == "hashed" })
        }
    }

    @Test
    fun `does nothing when admin already exists`() {
        every { userRepository.existsByEmail("admin@example.com") } returns true

        val bootstrapper = AdminBootstrapper("admin@example.com", "changeit123", userRepository, passwordEncoder)
        bootstrapper.run(args)

        verify(exactly = 0) { userRepository.save(any()) }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.security.AdminBootstrapperTest"`
Expected: FAIL — compile error, `AdminBootstrapper` doesn't exist yet.

- [ ] **Step 4: Create `AdminBootstrapper`**

```kotlin
package quiz.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository

@Component
class AdminBootstrapper(
    @Value("\${admin.email}") private val adminEmail: String,
    @Value("\${admin.password}") private val adminPassword: String,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (userRepository.existsByEmail(adminEmail)) {
            return
        }
        userRepository.save(
            User(
                id = createId(),
                email = adminEmail,
                password = passwordEncoder.encode(adminPassword) ?: "",
                role = Role.ADMIN
            )
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.security.AdminBootstrapperTest"`
Expected: PASS (both tests).

- [ ] **Step 6: Run the full suite to confirm app context still boots with the new runner**

Run: `./gradlew test --tests "quiz.webquiz.WebQuizApplicationTests"`
Expected: PASS — confirms `AdminBootstrapper` wires cleanly into the Spring context using the test properties from Task 1.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/quiz/security/AdminBootstrapper.kt src/main/resources/application.properties src/test/kotlin/quiz/security/AdminBootstrapperTest.kt
git commit -m "feat: seed a bootstrap admin account on startup"
```

---

## Task 8: `UserNotFoundException` + 404 mapping in `GlobalExceptionHandler`

**Note:** `GlobalExceptionHandler` and the exception package layout changed
ahead of this plan (now `quiz.domain.exception`, structured
`quiz.api.error.ErrorResponse(status, error, message)` bodies, plus
generic `IllegalArgumentException`/`DataIntegrityViolationException`/
`Exception` fallback handlers already in place). This task follows that
current structure, not the older plain-`String`-body version referenced
in the design spec.

**Files:**
- Create: `src/main/kotlin/quiz/domain/exception/UserNotFoundException.kt`
- Modify: `src/main/kotlin/GlobalExceptionHandler.kt`
- Test: `src/test/kotlin/GlobalExceptionHandlerTest.kt`

**Interfaces:**
- Consumes: `quiz.api.error.ErrorResponse(status: Int, error: String, message: String)` (existing), `GlobalExceptionHandler.buildError(status, errorCode, message): ResponseEntity<ErrorResponse>` (existing private helper).
- Produces: `quiz.domain.exception.UserNotFoundException(message: String) : RuntimeException(message)`; `GlobalExceptionHandler.handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse>` returning HTTP 404 with error code `USER_NOT_FOUND`.

- [ ] **Step 1: Write the failing test**

```kotlin
package quiz

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import quiz.domain.exception.UserNotFoundException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `UserNotFoundException maps to 404 with USER_NOT_FOUND error code`() {
        val response = handler.handleUserNotFound(UserNotFoundException("no such user"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("USER_NOT_FOUND", response.body?.error)
        assertEquals("no such user", response.body?.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.GlobalExceptionHandlerTest"`
Expected: FAIL — compile error, `UserNotFoundException` and `handleUserNotFound` don't exist yet.

- [ ] **Step 3: Create `UserNotFoundException`**

```kotlin
package quiz.domain.exception

class UserNotFoundException(message: String) : RuntimeException(message)
```

- [ ] **Step 4: Add the handler to the existing `GlobalExceptionHandler`**

Add the import `import quiz.domain.exception.UserNotFoundException` and this method inside the existing `GlobalExceptionHandler` class (alongside `handleDuplicateEmail`, before the generic `IllegalArgumentException`/`Exception` handlers so it takes precedence for this specific type):

```kotlin
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.message ?: "User not found")
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.GlobalExceptionHandlerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/quiz/domain/exception/UserNotFoundException.kt src/main/kotlin/GlobalExceptionHandler.kt src/test/kotlin/GlobalExceptionHandlerTest.kt
git commit -m "feat: add UserNotFoundException with 404 mapping"
```

---

## Task 9: Promotion endpoint (`UserService.promoteToAdmin` + `AdminController`)

**Files:**
- Modify: `src/main/kotlin/quiz/domain/service/UserService.kt`
- Modify: `src/main/kotlin/quiz/domain/service/UserServiceImpl.kt`
- Create: `src/main/kotlin/quiz/controller/AdminController.kt`
- Test: `src/test/kotlin/quiz/domain/service/UserServiceImplTest.kt` (extend from Task 6)
- Test: `src/test/kotlin/quiz/controller/AdminControllerIntegrationTest.kt`

**Interfaces:**
- Consumes: `quiz.domain.repository.UserRepository.findByEmail(email): User?` / `.save(User): User` (existing), `quiz.domain.exception.UserNotFoundException` (Task 8).
- Produces: `UserService.promoteToAdmin(email: String)`; `POST /api/admin/users/{email}/promote` in `AdminController`, gated by the `hasRole("ADMIN")` matcher already added in Task 4.

- [ ] **Step 1: Write the failing unit test (extend `UserServiceImplTest`)**

Add to `src/test/kotlin/quiz/domain/service/UserServiceImplTest.kt`:

```kotlin
    @Test
    fun `promoteToAdmin sets role to ADMIN and saves`() {
        val existing = User(id = "1", email = "someone@example.com", password = "hashed", role = Role.USER)
        every { userRepository.findByEmail("someone@example.com") } returns existing
        val savedUser = slot<User>()
        every { userRepository.save(capture(savedUser)) } answers { savedUser.captured }

        service.promoteToAdmin("someone@example.com")

        assertEquals(Role.ADMIN, savedUser.captured.role)
        assertEquals("someone@example.com", savedUser.captured.email)
    }

    @Test
    fun `promoteToAdmin throws UserNotFoundException for unknown email`() {
        every { userRepository.findByEmail("ghost@example.com") } returns null

        kotlin.test.assertFailsWith<quiz.domain.exception.UserNotFoundException> {
            service.promoteToAdmin("ghost@example.com")
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.domain.service.UserServiceImplTest"`
Expected: FAIL — compile error, `UserService`/`UserServiceImpl` have no `promoteToAdmin` method.

- [ ] **Step 3: Add `promoteToAdmin` to the interface**

```kotlin
package quiz.domain.service

interface UserService {
    fun registerUser(email: String, rawPassword: String)
    fun promoteToAdmin(email: String)
}
```

- [ ] **Step 4: Implement `promoteToAdmin` in `UserServiceImpl`**

Add to `UserServiceImpl` (alongside the existing `registerUser`):

```kotlin
    override fun promoteToAdmin(email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw UserNotFoundException("No user with email: $email")
        userRepository.save(user.copy(role = Role.ADMIN))
    }
```

Add the import: `import quiz.domain.exception.UserNotFoundException`.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.domain.service.UserServiceImplTest"`
Expected: PASS (all tests in the file, including the two new ones).

- [ ] **Step 6: Write the failing integration test for the endpoint**

```kotlin
package quiz.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import quiz.domain.Role
import quiz.domain.User
import quiz.domain.createId
import quiz.domain.repository.UserRepository

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminControllerIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @BeforeEach
    fun setUp() {
        if (!userRepository.existsByEmail("plain-user@example.com")) {
            userRepository.save(
                User(
                    id = createId(),
                    email = "plain-user@example.com",
                    password = passwordEncoder.encode("password123"),
                    role = Role.USER
                )
            )
        }
        if (!userRepository.existsByEmail("test-admin@example.com")) {
            userRepository.save(
                User(
                    id = createId(),
                    email = "test-admin@example.com",
                    password = passwordEncoder.encode("password123"),
                    role = Role.ADMIN
                )
            )
        }
        if (!userRepository.existsByEmail("promote-me@example.com")) {
            userRepository.save(
                User(
                    id = createId(),
                    email = "promote-me@example.com",
                    password = passwordEncoder.encode("password123"),
                    role = Role.USER
                )
            )
        }
    }

    @Test
    fun `non-admin cannot promote`() {
        val response = restTemplate
            .withBasicAuth("plain-user@example.com", "password123")
            .postForEntity("/api/admin/users/promote-me@example.com/promote", null, String::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `admin can promote another user, who can then reach admin endpoints`() {
        val promote = restTemplate
            .withBasicAuth("test-admin@example.com", "password123")
            .postForEntity("/api/admin/users/promote-me@example.com/promote", null, String::class.java)
        assertEquals(HttpStatus.OK, promote.statusCode)

        val secondPromotion = restTemplate
            .withBasicAuth("promote-me@example.com", "password123")
            .postForEntity("/api/admin/users/plain-user@example.com/promote", null, String::class.java)
        assertEquals(HttpStatus.OK, secondPromotion.statusCode)
    }

    @Test
    fun `promoting unknown email returns 404`() {
        val response = restTemplate
            .withBasicAuth("test-admin@example.com", "password123")
            .postForEntity("/api/admin/users/nobody@example.com/promote", null, String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew test --tests "quiz.controller.AdminControllerIntegrationTest"`
Expected: FAIL — 404 for all requests, `AdminController` doesn't exist yet.

- [ ] **Step 8: Create `AdminController`**

```kotlin
package quiz.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import quiz.domain.service.UserService

@RestController
class AdminController(private val userService: UserService) {

    @PostMapping("/api/admin/users/{email}/promote")
    fun promote(@PathVariable email: String): ResponseEntity<Void> {
        userService.promoteToAdmin(email)
        return ResponseEntity.ok().build()
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew test --tests "quiz.controller.AdminControllerIntegrationTest"`
Expected: PASS (all 3 tests). This also confirms the `/api/admin/**` `hasRole("ADMIN")` matcher from Task 4 is wired correctly end-to-end.

- [ ] **Step 10: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests across every task pass together.

- [ ] **Step 11: Commit**

```bash
git add src/main/kotlin/quiz/domain/service/UserService.kt src/main/kotlin/quiz/domain/service/UserServiceImpl.kt src/main/kotlin/quiz/controller/AdminController.kt src/test/kotlin/quiz/domain/service/UserServiceImplTest.kt src/test/kotlin/quiz/controller/AdminControllerIntegrationTest.kt
git commit -m "feat: add admin-only endpoint to promote a user to admin"
```
