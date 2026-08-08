# Advanced Queries (Pagination + Completions) — Design Spec

Date: 2026-07-28
Project: WebQuizKotlin (`~/IdeaProjects/WebQuizKotlin`)

## Goal

Final Hyperskill stage for this quiz engine:

1. `GET /api/quizzes?page={n}` returns a paginated `Page<QuizResponse>` (10
   quizzes per page) instead of the full unpaged list.
2. `GET /api/quizzes/completed?page={n}` returns a paginated list of the
   authenticated user's successful quiz completions, newest first.

Both endpoints already require authentication (existing `SecurityConfig`
rule `anyRequest().authenticated()`), so 401-on-unauthenticated is already
satisfied with no security config changes.

## Non-goals

- No client-controlled page `size` or `sort` query params — page size is
  fixed at 10, sort order for completions is fixed (newest first). This
  matches the task's literal wording (`?page={number}` only) and avoids
  surprises against Hyperskill's external test suite.
- No FK/`@ManyToOne` relations for completions — consistent with how
  `Quiz.author` already stores a plain email string with no relation to
  `User`.
- Completions are recorded only for **correct** answers ("successful
  completions"), not every solve attempt.
- No change to `POST /api/quizzes`, `GET /api/quizzes/{id}`, `DELETE
  /api/quizzes/{id}` response shapes or behavior.

## Architecture

### New package `engine.completion`

- `QuizCompletion.kt` — `@Entity @Table(name = "quiz_completions")`:
  `id: Int?` (auto), `quizId: Int` (not null), `userEmail: String` (not
  null), `completedAt: OffsetDateTime` (not null).
- `QuizCompletionRepository.kt` — `JpaRepository<QuizCompletion, Int>` plus
  `fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageable:
  Pageable): Page<QuizCompletion>`.
- `dto/CompletionResponse.kt` — `data class CompletionResponse(val id: Int,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") val completedAt:
  OffsetDateTime)`. `id` here is the **quiz id** (`quizId` from the entity),
  per the task's example (`{"id": <quiz id>, "completedAt": ...}`).

### `QuizService` changes

- `getAllQuizzes(pageable: Pageable): Page<QuizResponse>` — replaces the
  existing `getAllQuizzes(): List<QuizResponse>`. Implementation:
  `quizRepository.findAll(pageable).map { it.toResponse() }`.
- `solveQuiz(id: Int, answerList: List<Int>, userEmail: String):
  AnswerResponse?` — gains a `userEmail` parameter. On a correct answer
  (`success = true`), saves a `QuizCompletion(quizId = id, userEmail =
  userEmail, completedAt = OffsetDateTime.now())` via
  `quizCompletionRepository.save(...)` before returning the response. On a
  wrong answer, no completion row is written.
- `getCompletions(userEmail: String, pageable: Pageable):
  Page<CompletionResponse>` — new method:
  `quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(userEmail,
  pageable).map { CompletionResponse(id = it.quizId, completedAt =
  it.completedAt) }`.
- `QuizService` constructor gains `quizCompletionRepository:
  QuizCompletionRepository` as a second dependency.

### `QuizController` changes

- `getAllQuizzes(@RequestParam(defaultValue = "0") page: Int):
  Page<QuizResponse>` — builds `PageRequest.of(page, 10)` internally and
  calls `quizService.getAllQuizzes(pageable)`. Replaces the current
  no-param version. Return type changes from `List<QuizResponse>` to
  `Page<QuizResponse>` (Spring serializes this to the exact JSON shape the
  task describes: `content`, `totalPages`, `totalElements`, `last`,
  `first`, `sort`, `number`, `numberOfElements`, `size`, `empty`,
  `pageable`).
- `solveQuiz(@PathVariable id: Int, @RequestBody request: SolveQuizRequest,
  authentication: Authentication): ResponseEntity<AnswerResponse>` — gains
  an `Authentication` parameter, passes `authentication.name` through to
  `quizService.solveQuiz(id, request.answer, authentication.name)`.
- New endpoint: `getCompletedQuizzes(@RequestParam(defaultValue = "0") page:
  Int, authentication: Authentication): Page<CompletionResponse>` — builds
  `PageRequest.of(page, 10)`, calls `quizService.getCompletions(
  authentication.name, pageable)`.

## Data flow

1. **List quizzes (paginated):** `GET /api/quizzes?page=1` → Spring Security
   authenticates via HTTP Basic (already wired) → controller builds
   `PageRequest.of(1, 10)` → `QuizService.getAllQuizzes` →
   `quizRepository.findAll(pageable)` → each `Quiz` mapped to `QuizResponse`
   (unchanged mapping, no `author`/`answer` leakage) → Spring serializes the
   `Page<QuizResponse>` directly to the task's documented JSON shape.
   Unauthenticated → 401 (existing `SecurityConfig` behavior, unchanged).

2. **Solve a quiz (records completion on success):** `POST
   /api/quizzes/{id}/solve` → controller extracts `authentication.name` →
   `QuizService.solveQuiz` compares submitted answer to `quiz.answer`; if
   correct, saves a `QuizCompletion` row stamped with the current
   `OffsetDateTime` and the solver's email, then returns
   `AnswerResponse(success = true, ...)`. If wrong, no completion row is
   written, returns `AnswerResponse(success = false, ...)`. This endpoint's
   existing 200/404 behavior and response shape are otherwise unchanged.

3. **List completions (paginated):** `GET /api/quizzes/completed?page=0` →
   controller extracts `authentication.name` and builds `PageRequest.of(0,
   10)` → `QuizService.getCompletions` →
   `quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(email,
   pageable)` → each `QuizCompletion` mapped to `CompletionResponse(id =
   quizId, completedAt)` → Spring serializes the `Page<CompletionResponse>`.
   Unauthenticated → 401 (existing `SecurityConfig` behavior, unchanged).

## Testing / verification plan

No new automated tests — consistent with prior stages of this project,
which rely on Hyperskill's external test suite plus manual `curl`
verification. Before declaring done, manually verify via `curl`:

- Create 12+ quizzes as one user; `GET /api/quizzes?page=0` returns exactly
  10 in `content`, `totalElements` reflects the true total, `totalPages`
  reflects `ceil(total/10)`, `first=true`, `last=false`.
- `GET /api/quizzes?page=1` (second page) returns the remaining quizzes,
  `last=true`.
- `GET /api/quizzes` with no auth → 401.
- Solve a quiz correctly, then `GET /api/quizzes/completed?page=0` → the
  completion appears with the right quiz `id` and an ISO-8601 timestamp
  with an offset (`...SSSXXX` format, e.g. `+00:00` or `Z`-equivalent).
- Solve a quiz incorrectly → confirm no completion row appears for that
  attempt.
- Solve the same quiz twice successfully → confirm both completions appear
  as separate rows, newest first.
- `GET /api/quizzes/completed` with no auth → 401.
- If there are zero quizzes or zero completions, confirm `content: []` and
  `empty: true`.
- Confirm existing endpoints (`POST /api/quizzes`, `GET /api/quizzes/{id}`,
  `DELETE /api/quizzes/{id}`) are unaffected.

## Open questions / risks

None outstanding — all decisions confirmed with the user during
brainstorming (completions only recorded on correct answers; page size
fixed at 10 with no client-controlled size/sort; no FK relations for
completions, consistent with existing `Quiz.author` pattern; `completedAt`
serialized with an explicit timezone offset per the task's literal format
string, even though the task's own example output omits one).
