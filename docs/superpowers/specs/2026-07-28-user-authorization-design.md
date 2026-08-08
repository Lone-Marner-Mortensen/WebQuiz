# User Authorization — Design Spec

Date: 2026-07-28
Project: WebQuizKotlin (`~/IdeaProjects/WebQuizKotlin`)

## Goal

Add user registration and HTTP Basic authentication/authorization to the Web
Quiz Engine so that:

- Anyone can register with an email + password.
- All quiz operations (create, get one, get all, solve, delete) require a
  registered, authenticated user.
- A user may only delete quizzes they created.
- Passwords are never stored in plain text.

This is a Hyperskill (JetBrains Academy) project stage; grading happens via
an external test suite, not the project's own tests.

## Non-goals

- No PUT/PATCH quiz update endpoint (optional per the task, skipped for now).
- No refresh tokens / JWT / session-based auth — HTTP Basic only, as required.
- No roles/admin distinction — every registered user has equal privileges.
- No change to existing quiz creation/get/list/solve response shapes or
  validation rules.

## Architecture

### New package `engine.user`

- `User.kt` — `@Entity` with `id` (auto), `email` (unique, not null),
  `password` (BCrypt hash, not null).
- `UserRepository.kt` — `JpaRepository<User, Int>` plus
  `findByEmail(email: String): User?`.
- `dto/RegisterRequest.kt` — `email: String?` (`@field:Email @field:NotBlank`),
  `password: String?` (`@field:NotBlank @field:Size(min = 5)`).
- `UserService.kt` — `registerUser(request): Unit`, throws
  `DuplicateEmailException` if email taken; hashes password with the
  `PasswordEncoder` bean before saving.
- `AppUserDetailsService.kt` — implements Spring Security's
  `UserDetailsService`; `loadUserByUsername(email)` looks up via
  `UserRepository`, throws `UsernameNotFoundException` if absent, returns a
  Spring Security `User` (org.springframework.security.core.userdetails.User)
  built from the stored email/password hash with no extra authorities.

### New `UserController.kt`

- `POST /api/register` → `@Valid @RequestBody RegisterRequest` →
  `userService.registerUser(request)` → `ResponseEntity.ok().build()` (200,
  empty body).

### New `SecurityConfig.kt`

- `@Bean SecurityFilterChain`: `httpBasic()`, `sessionManagement { STATELESS }`,
  `csrf { disable() }`, authorization rules: `permitAll()` for
  `POST /api/register` and `POST /actuator/shutdown`; `authenticated()` for
  everything else.
- `@Bean PasswordEncoder` → `BCryptPasswordEncoder()`.
- `@Bean AuthenticationProvider` → `DaoAuthenticationProvider` wired to
  `AppUserDetailsService` + the `PasswordEncoder`.

### `Quiz` entity change

- Add `author: String = ""` column (not null), storing the creating user's
  email.

### `QuizService` / `QuizController` changes

- `createQuiz(request, authorEmail)` — sets `author = authorEmail` on the
  new `Quiz`. Controller passes `authentication.name` from the injected
  `Authentication` (or `Principal`) parameter.
- New `deleteQuiz(id, requesterEmail): DeleteResult` where `DeleteResult` is
  one of `NotFound`, `Forbidden`, `Deleted` — controller maps to
  404 / 403 / 204 respectively.
- `getQuiz`/`getAllQuizzes`/`solveQuiz` behavior and response shapes are
  unchanged (no `author` field leaks into `QuizResponse`).

### `GlobalExceptionHandler.kt` (`@RestControllerAdvice`)

Replaces the inline `@ExceptionHandler` currently in `QuizController`.

- `MethodArgumentNotValidException` → 400 (existing behavior, moved here).
- `DuplicateEmailException` → 400.

## Data flow

1. **Register**: client `POST /api/register` with email/password JSON.
   `@Valid` enforces email format and 5+ char password → 400 if invalid.
   `UserService` checks `UserRepository.findByEmail`; if present → throws
   `DuplicateEmailException` → 400. Otherwise BCrypt-hashes the password,
   saves `User`, returns 200 with empty body.
2. **Authenticated request** (create/get/list/solve/delete quiz): Spring
   Security's Basic Auth filter decodes the `Authorization` header,
   `AppUserDetailsService` loads the user by email, `DaoAuthenticationProvider`
   verifies the BCrypt hash. Missing/invalid credentials → 401 automatically
   (Spring Security default entry point). On success, the request reaches
   the controller with an `Authentication` whose `.name` is the email.
3. **Create quiz**: controller extracts `authentication.name`, passes it to
   `QuizService.createQuiz` as `author`.
4. **Delete quiz**: controller extracts `authentication.name`, calls
   `QuizService.deleteQuiz(id, email)`. Service loads the quiz; not found →
   404; found but `quiz.author != email` → 403; else deletes and returns
   success → 204 with no body.

## Testing / verification plan

No new automated tests are added beyond the existing `contextLoads` smoke
test — Hyperskill grades this stage via its own hidden test suite. Before
declaring the work done, manually verify via `curl` against the running app:

- Register a new user → 200.
- Register the same email again → 400.
- Register with invalid email format → 400.
- Register with a <5 char password → 400.
- `GET /api/quizzes` with no credentials → 401.
- `GET /api/quizzes` with valid credentials → 200 (and response has no
  `author`/`answer` leakage — matches prior response shape).
- Create a quiz as user A → 200, verify `author` is stamped internally.
- Delete that quiz as user A → 204.
- Create another quiz as user A, attempt delete as user B → 403.
- Attempt delete of a nonexistent quiz id → 404.
- Confirm existing create/get/list/solve validation and behavior are
  unchanged from before this change (still 400 on missing title/text/options
  <2).
- Confirm `POST /actuator/shutdown` remains accessible without auth.

## Open questions / risks

None outstanding — all decisions confirmed with the user during
brainstorming (author-email ownership field, `engine.user` package mirroring
`engine.quiz`, stateless HTTP Basic security config, global exception
handler, no git repo for this project).
