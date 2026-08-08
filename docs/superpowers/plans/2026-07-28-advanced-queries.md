# Advanced Queries (Pagination + Completions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paginate `GET /api/quizzes` and add a paginated `GET /api/quizzes/completed` endpoint that records/returns the authenticated user's successful quiz completions.

**Architecture:** New `engine.completion` package (entity + repository + response DTO) tracks quiz completions on correct answers. `QuizService`/`QuizController` are updated to build a fixed-size (10) `PageRequest` from a `page` query param and return `Page<T>` instead of `List<T>`.

**Tech Stack:** Kotlin 2.2.20, Spring Boot 4.1.0, Spring Data JPA (`Page`/`Pageable`/`PageRequest`), H2, Jackson (`@JsonFormat`).

## Global Constraints

- Page size is fixed at 10 for both paginated endpoints — no client-controlled `size` or `sort` query params (per spec's non-goals).
- A completion is recorded only when the submitted answer is correct ("successful completions") — wrong answers never create a `QuizCompletion` row.
- Completions have no FK/`@ManyToOne` relations — plain `quizId: Int` and `userEmail: String` fields, consistent with how `Quiz.author` already stores a plain email string.
- `completedAt` must serialize with the exact pattern `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` (includes a timezone offset), per the spec's literal requirement — even though the task's own prose example omits an offset.
- `GET /api/quizzes/completed` results are sorted by `completedAt` descending (newest first).
- No changes to `POST /api/quizzes`, `GET /api/quizzes/{id}`, `DELETE /api/quizzes/{id}` response shapes or behavior.
- Both endpoints already require authentication via the existing `SecurityConfig` rule `anyRequest().authenticated()` — no `SecurityConfig` changes needed; unauthenticated requests already get 401 automatically.
- Project root: `/Users/lonemarnermortensen/IdeaProjects/WebQuizKotlin`. Not a git repo — skip git add/commit steps; use `./gradlew build -q` and `./gradlew bootRun` for verification instead.

---

### Task 1: QuizCompletion entity and repository

**Files:**
- Create: `src/main/kotlin/engine/completion/QuizCompletion.kt`
- Create: `src/main/kotlin/engine/completion/QuizCompletionRepository.kt`

**Interfaces:**
- Consumes: nothing (new package).
- Produces:
  - `QuizCompletion(id: Int? = null, quizId: Int = 0, userEmail: String = "", completedAt: OffsetDateTime = OffsetDateTime.now())` — JPA entity, table `quiz_completions`.
  - `QuizCompletionRepository : JpaRepository<QuizCompletion, Int>` with `fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageable: Pageable): Page<QuizCompletion>`.

- [ ] **Step 1: Create the `QuizCompletion` entity**

```kotlin
package engine.completion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "quiz_completions")
data class QuizCompletion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false)
    var quizId: Int = 0,

    @Column(nullable = false)
    var userEmail: String = "",

    @Column(nullable = false)
    var completedAt: OffsetDateTime = OffsetDateTime.now()
)
```

- [ ] **Step 2: Create the `QuizCompletionRepository`**

```kotlin
package engine.completion

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface QuizCompletionRepository : JpaRepository<QuizCompletion, Int> {
    fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageable: Pageable): Page<QuizCompletion>
}
```

- [ ] **Step 3: Compile check**

Run: `./gradlew compileKotlin -q`
Expected: build succeeds with no errors.

---

### Task 2: CompletionResponse DTO

**Files:**
- Create: `src/main/kotlin/engine/quiz/dto/CompletionResponse.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `CompletionResponse(id: Int, completedAt: OffsetDateTime)` — `id` here is the **quiz id**, matching the task's documented shape `{"id": <quiz id>, "completedAt": ...}`.

- [ ] **Step 1: Create the `CompletionResponse` DTO**

```kotlin
package engine.quiz.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class CompletionResponse(
    val id: Int,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val completedAt: OffsetDateTime
)
```

- [ ] **Step 2: Compile check**

Run: `./gradlew compileKotlin -q`
Expected: build succeeds with no errors.

---

### Task 3: QuizService — paginated quiz listing, completion recording, and completion listing

**Files:**
- Modify: `src/main/kotlin/engine/service/QuizService.kt`

**Interfaces:**
- Consumes: `QuizCompletion`, `QuizCompletionRepository` (Task 1), `CompletionResponse` (Task 2).
- Produces:
  - `QuizService(quizRepository: QuizRepository, quizCompletionRepository: QuizCompletionRepository)` — constructor now takes a second dependency.
  - `getAllQuizzes(pageable: Pageable): Page<QuizResponse>` — replaces the old no-arg `getAllQuizzes(): List<QuizResponse>`.
  - `solveQuiz(id: Int, answerList: List<Int>, userEmail: String): AnswerResponse?` — gains a `userEmail` parameter.
  - `getCompletions(userEmail: String, pageable: Pageable): Page<CompletionResponse>` — new method.

- [ ] **Step 1: Rewrite `QuizService.kt` in full**

Replace the entire file with:

```kotlin
package engine.service

import engine.completion.QuizCompletion
import engine.completion.QuizCompletionRepository
import engine.quiz.Quiz
import engine.quiz.dto.AnswerResponse
import engine.quiz.dto.CompletionResponse
import engine.quiz.dto.CreateQuizRequest
import engine.quiz.dto.QuizResponse
import engine.repository.QuizRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

sealed class DeleteResult {
    object Deleted : DeleteResult()
    object NotFound : DeleteResult()
    object Forbidden : DeleteResult()
}

@Service
class QuizService(
    private val quizRepository: QuizRepository,
    private val quizCompletionRepository: QuizCompletionRepository
) {

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

    fun getAllQuizzes(pageable: Pageable): Page<QuizResponse> {
        return quizRepository.findAll(pageable).map { it.toResponse() }
    }

    fun solveQuiz(id: Int, answerList: List<Int>, userEmail: String): AnswerResponse? {
        val quiz = quizRepository.findById(id).orElse(null) ?: return null
        return if (answerList.sorted() == quiz.answer.sorted()) {
            quizCompletionRepository.save(
                QuizCompletion(quizId = id, userEmail = userEmail, completedAt = OffsetDateTime.now())
            )
            AnswerResponse(success = true, feedback = "Congratulations, you're right!")
        } else {
            AnswerResponse(success = false, feedback = "Wrong answer! Please, try again.")
        }
    }

    fun getCompletions(userEmail: String, pageable: Pageable): Page<CompletionResponse> {
        return quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(userEmail, pageable)
            .map { CompletionResponse(id = it.quizId, completedAt = it.completedAt) }
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

- [ ] **Step 2: Compile check**

Run: `./gradlew compileKotlin -q`
Expected: build FAILS at this point — `QuizController` still calls the old `getAllQuizzes()` (no args) and the old `solveQuiz(id, answer)` (no `userEmail`) signatures. This is expected; Task 4 fixes the call sites. Confirm the only errors are in `QuizController.kt` referencing `getAllQuizzes`/`solveQuiz`, not in `QuizService.kt` itself.

---

### Task 4: QuizController — paginated endpoints and new completed-quizzes endpoint

**Files:**
- Modify: `src/main/kotlin/engine/controller/QuizController.kt`

**Interfaces:**
- Consumes: `QuizService.getAllQuizzes(pageable)`, `QuizService.solveQuiz(id, answer, userEmail)`, `QuizService.getCompletions(userEmail, pageable)` (Task 3).
- Produces: `GET /api/quizzes?page={n}` → `Page<QuizResponse>`; `GET /api/quizzes/completed?page={n}` → `Page<CompletionResponse>`.

- [ ] **Step 1: Rewrite `QuizController.kt` in full**

Replace the entire file with:

```kotlin
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
```

Note: the `GET /api/quizzes/completed` mapping is declared before nothing conflicting — Spring's path matching resolves `/api/quizzes/completed` to this handler and `/api/quizzes/{id}` only matches other numeric-looking segments, so there is no route ambiguity between `getQuiz` and `getCompletedQuizzes`.

- [ ] **Step 2: Full build check**

Run: `./gradlew build -q`
Expected: build succeeds with no errors (this resolves Task 3's expected intermediate failure).

---

### Task 5: End-to-end manual verification

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

- [ ] **Step 3: Register a user**

```bash
curl -s -o /dev/null -w "register: %{http_code}\n" -X POST http://localhost:8891/api/register \
  -H "Content-Type: application/json" -d '{"email":"pageuser@mail.org","password":"strongpassword"}'
# expect 200
```

- [ ] **Step 4: Create 12 quizzes as that user**

```bash
for i in $(seq 1 12); do
  curl -s -o /dev/null -w "create quiz $i: %{http_code}\n" -u pageuser@mail.org:strongpassword \
    -X POST http://localhost:8891/api/quizzes \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Quiz $i\",\"text\":\"Text $i\",\"options\":[\"a\",\"b\"],\"answer\":[0]}"
done
# expect 200 twelve times
```

- [ ] **Step 5: Verify pagination on `GET /api/quizzes`**

```bash
curl -s -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes?page=0" | python3 -m json.tool 2>/dev/null || \
curl -s -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes?page=0"
```
Expected: `content` has exactly 10 items, `totalElements` is 12 (or more, if other quizzes exist from prior manual testing — if so, note the actual total and adjust expectations below accordingly), `first` is `true`, `last` is `false`, `size` is 10, `number` is 0.

```bash
curl -s -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes?page=1"
```
Expected: `content` has the remaining items (2, if exactly 12 total), `last` is `true`, `number` is 1.

- [ ] **Step 6: Verify unauthenticated pagination request is rejected**

```bash
curl -s -o /dev/null -w "unauthenticated list: %{http_code}\n" "http://localhost:8891/api/quizzes?page=0"
# expect 401
```

- [ ] **Step 7: Solve one quiz correctly, one incorrectly**

First, get a quiz id to work with:
```bash
QUIZ_ID=$(curl -s -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes?page=0" | python3 -c "import sys,json; print(json.load(sys.stdin)['content'][0]['id'])")
echo "Using quiz id: $QUIZ_ID"
```

Solve correctly (answer index 0 matches every quiz created in Step 4):
```bash
curl -s -u pageuser@mail.org:strongpassword -X POST "http://localhost:8891/api/quizzes/$QUIZ_ID/solve" \
  -H "Content-Type: application/json" -d '{"answer":[0]}'
# expect {"success":true,...}
```

Solve incorrectly:
```bash
curl -s -u pageuser@mail.org:strongpassword -X POST "http://localhost:8891/api/quizzes/$QUIZ_ID/solve" \
  -H "Content-Type: application/json" -d '{"answer":[1]}'
# expect {"success":false,...}
```

- [ ] **Step 8: Verify `GET /api/quizzes/completed` shows only the correct attempt**

```bash
curl -s -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes/completed?page=0"
```
Expected: `content` has exactly 1 item (from Step 7's correct solve only — the incorrect solve must NOT appear), with `id` equal to `$QUIZ_ID` and a `completedAt` string matching the pattern `yyyy-MM-ddTHH:mm:ss.SSS±HH:MM` (an explicit timezone offset present, e.g. ending in `+02:00`, `+00:00`, or `Z`). `totalElements` is 1, `first` and `last` are both `true`.

- [ ] **Step 9: Solve the same quiz correctly a second time; verify duplicate completions and ordering**

```bash
sleep 1
curl -s -u pageuser@mail.org:strongpassword -X POST "http://localhost:8891/api/quizzes/$QUIZ_ID/solve" \
  -H "Content-Type: application/json" -d '{"answer":[0]}'
# expect {"success":true,...}

curl -s -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes/completed?page=0"
```
Expected: `content` now has exactly 2 items, both with `id` equal to `$QUIZ_ID`, ordered newest-first (the second solve's `completedAt` timestamp is later and appears FIRST in `content`). `totalElements` is 2.

- [ ] **Step 10: Verify unauthenticated completions request is rejected**

```bash
curl -s -o /dev/null -w "unauthenticated completed: %{http_code}\n" "http://localhost:8891/api/quizzes/completed?page=0"
# expect 401
```

- [ ] **Step 11: Verify existing endpoints are unaffected**

```bash
curl -s -o /dev/null -w "get quiz: %{http_code}\n" -u pageuser@mail.org:strongpassword "http://localhost:8891/api/quizzes/$QUIZ_ID"
# expect 200

curl -s -o /dev/null -w "delete quiz: %{http_code}\n" -u pageuser@mail.org:strongpassword -X DELETE "http://localhost:8891/api/quizzes/$QUIZ_ID"
# expect 204
```

- [ ] **Step 12: Shut down and review**

```bash
curl -s -X POST http://localhost:8891/actuator/shutdown
# expect {"message":"Shutting down, bye..."}
```

Review every expected-vs-actual pair from Steps 5-11. If any mismatch, stop and debug before considering this plan complete — do not proceed to declare the feature done.

---

## Self-Review Notes

- **Spec coverage:** paginated `GET /api/quizzes` (Task 3+4, verified Task 5 Steps 5-6), `GET /api/quizzes/completed` with paging (Task 3+4, verified Task 5 Steps 8-10), successful-only completion recording (Task 3, verified Task 5 Step 8 excludes the wrong answer), newest-first ordering (Task 1's repository method + Task 5 Step 9), `completedAt` format with offset (Task 2's `@JsonFormat`, verified Task 5 Step 8), 401 on both endpoints when unauthenticated (existing `SecurityConfig`, verified Task 5 Steps 6+10), unchanged existing endpoints (verified Task 5 Step 11). All spec sections have a corresponding task.
- **Type consistency:** `QuizService.getAllQuizzes(pageable: Pageable): Page<QuizResponse>` (Task 3 definition) matches `QuizController.getAllQuizzes` call site (Task 4). `solveQuiz(id, answerList, userEmail)` signature matches between Task 3 and Task 4's call site. `getCompletions(userEmail, pageable): Page<CompletionResponse>` matches between Task 3 and Task 4. `CompletionResponse(id, completedAt)` field names match between Task 2's definition and Task 3's construction (`CompletionResponse(id = it.quizId, completedAt = it.completedAt)`).
- **No placeholders:** all steps contain full, exact code.
