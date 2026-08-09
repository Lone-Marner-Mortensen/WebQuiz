# Multi-Question Quiz Design

## Context

`Quiz` currently models exactly one question: `title`, `text` (the
question prompt), `options`, and `answer` are all flat fields on
`Quiz` itself. A quiz cannot contain more than one question. This
design changes `Quiz` to own a list of `Question`s, each with its own
`text`, `options`, and `answer`, so a single quiz can contain any
number of questions.

## Goals

- `Quiz` holds `title`, `author`, `questions: List<Question>`,
  `createdAt`. `Question` holds `text`, `options`, `answer` — the
  exact per-question fields `Quiz` has today, just scoped to one
  question instead of the whole quiz.
- Each `Question` has exactly one correct answer: `answer: Int`, a
  single index into that question's own `options` list (not a list
  of indices — this project has no multi-select questions).
- A quiz must have at least one question. Each question independently
  must have at least 2 options, and its `answer` index must be a
  valid index into its own `options` list (this mirrors the
  existing single-question validation, just applied per-question).
- Solving a quiz submits one selected option index per question, in
  question order. The quiz is solved correctly only if every
  question's submitted index matches that question's correct
  `answer` index — this matches today's single-question
  all-or-nothing pass/fail semantics, extended to multiple questions.
- Quiz responses never expose `answer` values (matching current
  behavior — `QuizResponseDto` today omits `answer` entirely).

## Non-goals

- No per-question scoring/partial credit — solving is still
  all-correct-or-fail, not "3 of 5 correct."
- No incremental/one-question-at-a-time solving flow — the client
  submits all answers in a single request, as it does today.
- No change to `QuizCompletion` (still records one completion per
  quiz solve, not per-question).
- No migration of old single-question data — `ddl-auto=update` will
  add new tables/columns for the new shape but will not drop or
  migrate the old `quizzes.text`, `quizzes.options`,
  `quizzes.answer`, `quiz_options`, `quiz_answers` columns/tables.
  These become inert leftovers on any existing dev database file,
  consistent with how this codebase has already handled prior schema
  changes (e.g. the removed `role` column).
- No UI/frontend work.

## Domain model

```kotlin
data class Question(
    val text: String,
    val options: List<String>,
    val answer: Int
)

data class Quiz(
    val id: String,
    val title: String,
    val author: String,
    val questions: List<Question>,
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
```

`Quiz.title` now names the whole quiz (it no longer doubles as a
single question's prompt). `Question.text` is each question's own
prompt. `Question` has no `id` — it has no identity independent of
its position within `Quiz.questions`.

## Persistence layer

`QuestionDto` becomes its own JPA entity (a `Question` has two
`@ElementCollection`s of its own — options and answer — so it cannot
be a simple embeddable). `QuizDto` owns its questions via
`@OneToMany` with cascade-all and orphan removal, ordered by an
`@OrderColumn` so question order survives a reload:

```kotlin
@Entity
@Table(name = "quizzes")
class QuizDto(
    @Id val id: String,
    @Column(nullable = false) val title: String,
    @Column(nullable = false) val author: String,
    @OneToMany(mappedBy = "quiz", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "question_order")
    @Fetch(FetchMode.SUBSELECT)
    val questions: MutableList<QuestionDto>,
    @Column(nullable = false) val createdAt: OffsetDateTime
)

@Entity
@Table(name = "questions")
class QuestionDto(
    @Id val id: String,
    @Column(nullable = false) val text: String,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = [JoinColumn(name = "question_id")])
    @Column(name = "option_value", nullable = false)
    @Fetch(FetchMode.SUBSELECT)
    val options: List<String>,
    @Column(nullable = false)
    val answer: Int,
    @ManyToOne @JoinColumn(name = "quiz_id") val quiz: QuizDto?
)
```

`QuestionDto.id` is generated via the existing `quiz.domain.createId()`
helper when converting domain `Question` → `QuestionDto` — JPA entities
need stable identity that the identity-less domain `Question` doesn't
have.

## Mapper layer

`QuizDtoMapper.toDto` cannot be pure generated MapStruct for the
nested `questions` list, since generating a `QuestionDto.id` per
question is domain logic (calling `createId()`), not a field-to-field
mapping. This nested conversion is done in a default/helper method
that MapStruct's generated code calls, rather than left to automatic
list-element mapping.

`QuizDtoMapper.toDomain` drops `QuestionDto.id` when producing a
domain `Question` (which has no id) — MapStruct handles this
automatically as an unmapped source property, which is expected and
not an error.

## Service & business logic

`QuizService`:
- `createQuiz(title: String, author: String, questions: List<QuestionInput>): Quiz` —
  replaces the current flat-field signature. `QuestionInput` is a
  simple carrier for `text`/`options`/`answer` at the service
  boundary (distinct from the domain `Question` only in that it's
  the pre-validation input shape — see Open Questions).
  - Throws if `questions` is empty.
  - Throws `InvalidAnswerException` if any question's `answer` index
    is outside that question's own `options.indices`, or if any
    question has fewer than 2 options (extending the existing
    single-question validation added earlier, now applied
    per-question).
- `solveQuiz(id: String, answers: List<Int>, userEmail: String?): AnswerResult` —
  `answers[i]` (the selected option index) is compared against
  `quiz.questions[i].answer` for every index. Throws
  `InvalidAnswerException` if `answers.size != quiz.questions.size`
  (this is the same "malformed submitted answer" failure family as
  the existing out-of-range check, not a new exception type). Success
  requires every question's submitted index to equal that question's
  correct `answer` index — if all match, behavior is identical to
  today (records a `QuizCompletion` if `userEmail` is non-null,
  returns `AnswerResult(success = true, ...)`); otherwise
  `AnswerResult(success = false, ...)`.
- `getQuiz`, `getAllQuizzes`, `getCompletions`, `deleteQuiz` are
  unchanged in signature — they operate on `Quiz`/`QuizCompletion` as
  opaque values and don't inspect question structure.

## API layer

`CreateQuizRequestDto`:
```kotlin
data class CreateQuizRequestDto(
    @field:NotBlank val title: String?,
    @field:NotNull @field:Size(min = 1, message = "Quiz must have at least one question")
    val questions: List<QuestionRequestDto>?
)

data class QuestionRequestDto(
    @field:NotBlank val text: String?,
    @field:NotNull @field:Size(min = 2, message = "Options must contain at least 2 items")
    val options: List<String>?,
    val answer: Int?
)
```
`author` continues to come from `Authentication`, not the request
body (unchanged from today).

`SolveQuizRequestDto`:
```kotlin
data class SolveQuizRequestDto(
    val answers: List<Int> = emptyList()
)
```
(Renamed from `answer: List<Int>` to `answers: List<Int>` — one
selected option index per question, in question order.)

`QuizResponseDto`:
```kotlin
data class QuizResponseDto(
    val id: String,
    val title: String,
    val questions: List<QuestionResponseDto>
)

data class QuestionResponseDto(
    val text: String,
    val options: List<String>
)
```
`QuestionResponseDto` omits `answer`, preserving the existing
security-conscious behavior where solvers can't read the correct
answer from the quiz response.

`AnswerResultDto`/`AnswerResult` are unchanged (`{success, feedback}`)
per the all-or-nothing solve semantics above.

## Testing

- `QuizServiceImpl` unit tests: create with empty `questions` list
  fails; create with a question that has <2 options fails; create
  with an out-of-range answer index fails; create with valid
  multi-question input succeeds; solve with all-correct answers
  across all questions succeeds and records a completion; solve with
  any one question wrong fails (no completion recorded); solve with
  `answers.size != questions.size` fails with `InvalidAnswerException`.
- Mapper test: `QuizDtoMapper.toDto` generates a distinct `id` per
  `QuestionDto` and preserves question order; `toDomain` drops
  `QuestionDto.id` and preserves order.
- `SecurityConfigIntegrationTest`'s inline JSON bodies
  (`{"answer":[]}` for the solve-endpoint tests) update to the new
  `{"answers":[]}` shape so those tests keep compiling/passing against
  the new request DTO.

## Open questions / risks

- The service-boundary `QuestionInput` type (used by
  `createQuiz`'s `questions` parameter) is intentionally a separate,
  simple type from the domain `Question` — this avoids constructing a
  domain `Question` before validation has run. The implementation
  plan should confirm this stays a lightweight data holder and isn't
  conflated with the domain type during implementation.
- `@OrderColumn` requires the underlying `question_order` column to
  exist and be populated correctly by Hibernate — this is a
  well-established JPA pattern but worth a manual sanity check (e.g.
  create a 3-question quiz, fetch it back, confirm question order is
  preserved) during implementation, not just unit-test coverage.
