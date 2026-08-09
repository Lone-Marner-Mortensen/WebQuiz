# WebQuiz: Hexagonal Restructure — Design

> Updated 2026-08-23 to reflect the codebase's actual current structure
> after several rounds of refinement (subpackage reorganization,
> MapStruct adoption, String/UUID ids). Earlier revisions of this
> document described intermediate states that no longer exist; this
> version describes what's actually in the repo today.

## Purpose

`WebQuiz` has the same functionality as `WebQuizKotlin` (quiz CRUD,
solving, completions, user registration/auth, Swagger UI) but with a
different internal structure: a `domain` package tree that holds
entities, repository/service interfaces, and (as a deliberate,
explicitly-approved exception) the service implementations — with
almost no framework-specific implementation detail at the very top of
that tree, and explicit mapping between DTOs and domain objects at the
edges via MapStruct.

`WebQuizKotlin`'s structure couples domain entities directly to JPA
(`@Entity` on `Quiz`, `User`, `QuizCompletion`) and services directly
to Spring Data. This restructure separates those concerns using a
ports-and-adapters (hexagonal) style, adapted repeatedly based on
follow-up feedback as the codebase evolved.

## Package layout

```
src/main/kotlin/
  WebQuizApplication.kt
  GlobalExceptionHandler.kt
  ApiDocsConfig.kt

  quiz/
    domain/
      Quiz.kt
      User.kt
      QuizCompletion.kt
      Id.kt

      repository/
        QuizRepository.kt
        UserRepository.kt
        QuizCompletionRepository.kt

      response/
        PagedResult.kt
        DeleteResult.kt
        AnswerResult.kt
        DuplicateEmailException.kt

      service/
        QuizService.kt
        QuizServiceImpl.kt
        UserService.kt
        UserServiceImpl.kt

    repository/
      dto/
        QuizDto.kt
        UserDto.kt
        QuizCompletionDto.kt
      mapper/
        QuizDtoMapper.kt
        UserDtoMapper.kt
        QuizCompletionDtoMapper.kt
      QuizRepositoryImpl.kt
      UserRepositoryImpl.kt
      QuizCompletionRepositoryImpl.kt

    security/
      SecurityConfig.kt
      UserDetailsServiceAdapter.kt

    controller/
      QuizController.kt
      UserController.kt
      dto/
        CreateQuizRequestDto.kt
        QuizResponseDto.kt
        SolveQuizRequestDto.kt
        AnswerResultDto.kt
        QuizCompletionResponseDto.kt
        RegisterRequestDto.kt
      mapper/
        QuizMapper.kt
        CompletionMapper.kt
        AnswerMapper.kt
        PagedResultMapper.kt
```

## Layering rules

1. **`quiz.domain`** (top-level files: `Quiz.kt`, `User.kt`,
   `QuizCompletion.kt`) — pure Kotlin data classes, zero framework
   imports beyond `java.time.OffsetDateTime`. This is the strictest
   part of the tree.

2. **`quiz.domain.repository`** — plain Kotlin repository interfaces
   (`QuizRepository`, `UserRepository`, `QuizCompletionRepository`),
   no Spring Data / JPA types in their signatures. Pagination is
   expressed as plain `pageNumber: Int, pageSize: Int ->
   PagedResult<T>`, never `Pageable`/`Page<T>`.

3. **`quiz.domain.response`** — small domain-owned support types:
   `PagedResult<T>` (a domain-owned substitute for Spring's `Page<T>`),
   `DeleteResult` (sealed class), `AnswerResult`,
   `DuplicateEmailException`.

4. **`quiz.domain.service`** — holds both the service *interfaces*
   (`QuizService`, `UserService`) and their `@Service`-annotated
   *implementations* (`QuizServiceImpl`, `UserServiceImpl`). This is a
   deliberate, explicitly-requested exception to rule 1: putting
   implementation classes under `domain` means this subpackage (unlike
   the domain root) does carry Spring imports
   (`org.springframework.stereotype.Service`,
   `PasswordEncoder`). The top-level `quiz.domain` files remain
   completely framework-free; only this nested subpackage isn't.

5. **`quiz.repository`** — implements the domain repository interfaces
   as adapters over JPA:
   - `dto/` — JPA `@Entity` classes (`QuizDto`, `UserDto`,
     `QuizCompletionDto`). Despite the `Dto` suffix, these are **not**
     DTOs in the conventional sense — they're persistence entities
     mapped to database tables, named `XDto` at explicit request even
     though it collides in meaning with the unrelated
     `controller/dto/*Dto.kt` request/response DTOs (plain data
     classes with no framework annotations beyond `@JsonFormat`).
   - `mapper/` — MapStruct `@Mapper(componentModel = "spring")`
     interfaces (`QuizDtoMapper`, `UserDtoMapper`,
     `QuizCompletionDtoMapper`) converting `XDto` ↔ domain `X`.
     Implementations are generated at compile time by the MapStruct
     annotation processor (via kapt); nothing here is hand-written.
   - `QuizRepositoryImpl`, `UserRepositoryImpl`,
     `QuizCompletionRepositoryImpl` — each injects `EntityManager`
     directly (no Spring Data `JpaRepository` subinterfaces anywhere
     in this project) and hand-writes JPQL. This was settled after
     trying, and then removing, Spring Data derived-query interfaces
     for all three at different points — the goal was one consistent
     pattern across all three adapters, not per-adapter optimization.
     `save()` just maps the domain object (which already has an id —
     see "IDs are UUID strings" below) to its DTO and persists it;
     there is no `@GeneratedValue` database-native generation and no
     null-check-and-generate step in the repository layer anymore.

6. **`quiz.security`** — `SecurityConfig` (the `SecurityFilterChain`,
   password encoder, auth provider, permitted paths) and
   `UserDetailsServiceAdapter` (Spring Security's `UserDetailsService`,
   backed by the domain `UserRepository`). Both live here — not
   top-level — since every bean/class in this package is exclusively
   about security and `UserDetailsServiceAdapter` is `SecurityConfig`'s
   only real dependency.

7. **`quiz.controller`** — REST controllers, request/response DTOs
   (`controller/dto/*Dto.kt`), and MapStruct mappers
   (`controller/mapper/*Mapper.kt`) converting domain objects to
   response DTOs. Controllers depend only on domain service interfaces
   — never on `quiz.repository` or `quiz.security` classes directly;
   Spring wires the interface to its `@Service`/`@Component`
   implementation. `PagedResultMapper.toPage()` is the one exception
   to "everything here is MapStruct": it converts the domain's generic
   `PagedResult<T>` to Spring's generic `Page<T>`, which MapStruct
   doesn't have a natural mechanism for (it maps named type pairs, not
   generic containers), so it stays a plain Kotlin extension function.

8. **Top-level** (`WebQuizApplication.kt`, `GlobalExceptionHandler.kt`,
   `ApiDocsConfig.kt`) — cross-cutting Spring config with no single
   layer package that clearly fits: `WebQuizApplication` is the
   `@SpringBootApplication` entry point; `GlobalExceptionHandler` is a
   `@RestControllerAdvice` that translates domain exceptions
   (`DuplicateEmailException`) and validation failures into HTTP
   responses (a controller-layer concern despite its top-level
   location, since it doesn't have an existing home in `controller`
   that predates it); `ApiDocsConfig` declares an `OpenAPI` bean
   setting the Swagger UI title/version/description (named
   `ApiDocsConfig` rather than `OpenApiConfig` since "OpenAPI" as a
   word was read as implying the API itself is open/unauthenticated,
   which it isn't — auth is entirely `SecurityConfig`'s concern,
   unrelated to this bean).

   An earlier `OpenApiConfig` also existed briefly to declare a
   `basicAuth` Swagger security scheme (so Swagger UI would show an
   Authorize button); it was tried and then deliberately removed —
   Swagger UI still lists every endpoint without it, just without an
   Authorize button, and testing authenticated endpoints now requires
   curl/Postman instead. `springdoc-openapi-starter-webmvc-ui` stays
   as a dependency regardless, since the endpoint listing itself is
   still wanted.

   `quiz.repository` and `quiz.security` are top-level peers of
   `quiz.domain`/`quiz.controller` rather than nested under a shared
   `infrastructure` package — this keeps outbound adapters
   (`repository`, `security`) structurally symmetric with the inbound
   adapter (`controller`), instead of arbitrarily promoting one
   adapter category over the other.

## IDs are UUID strings, not database auto-increment integers

`Quiz.id`, `User.id`, `QuizCompletion.id`, and `QuizCompletion.quizId`
are all `String` (UUIDs), not `Int`. This was a deliberate change from
the original design (which used JPA `@GeneratedValue(strategy =
GenerationType.IDENTITY)` auto-increment integers):

- **Generation**: `Quiz.id`/`User.id`/`QuizCompletion.id` are
  non-nullable `String` with **no default value** — every
  construction site must pass one explicitly. An earlier version of
  this change gave `id` a constructor default of
  `UUID.randomUUID().toString()` (the same pattern `createdAt` uses),
  but that was reverted: a default on an identity field means any
  construction that *forgets* to pass `id` — most dangerously, a
  mapper reconstructing an existing domain object from a loaded JPA
  row — would silently get a fresh random id instead of a compile
  error. `createdAt` doesn't have this risk (a wrong timestamp is
  just wrong data; a wrong id is corrupted identity), so the two
  fields don't actually share the same justification for defaulting.
  Each `*ServiceImpl` now generates an id explicitly at the one place
  that's genuinely creating something new (`QuizServiceImpl.createQuiz`,
  the `QuizCompletion` built inside `QuizServiceImpl.solveQuiz`,
  `UserServiceImpl.registerUser`), via a shared `createId(): String`
  top-level function in `quiz/domain/Id.kt` (`UUID.randomUUID().toString()`
  under the hood) rather than repeating that expression at each call
  site. It's a plain top-level function rather than an extension on
  `UUID` itself, since `java.util.UUID` is a Java class with no
  companion object for Kotlin to attach an extension to.
  `*RepositoryImpl.save()` doesn't generate or null-check an id at
  all — it just maps and persists whatever the domain object already
  has. There's no database-native id generation (`@Id` with no
  `@GeneratedValue` on the JPA entity side). The JPA `*Dto` entity
  classes (`QuizDto`, `UserDto`, `QuizCompletionDto`) also have a
  non-nullable `id: String = ""`, matching every other field on those
  classes (`title: String = ""`, `author: String = ""`, etc.) — the
  `kotlin-jpa` Gradle plugin generates the no-arg constructor Hibernate
  needs from each property's default value, so `""` plays the same
  role there that `null` used to.
- **Ordering**: `Quiz` gained a `createdAt: OffsetDateTime` field
  (defaulted to `OffsetDateTime.now()`), because UUIDs have no
  meaningful sort order the way auto-increment integers did.
  `QuizRepositoryImpl.findAll`'s JPQL orders by `q.createdAt` instead
  of `q.id`, preserving the original "oldest first" pagination
  behavior explicitly rather than relying on id happening to be
  sequential. `createdAt` is not exposed in `QuizResponseDto` — it
  exists purely for internal ordering.
- **HTTP surface**: `@PathVariable id: Int` became `id: String` in
  `QuizController` (`getQuiz`, `solveQuiz`, `deleteQuiz`).
  `QuizResponseDto.id` and `QuizCompletionResponseDto.id` are `String`.
  An unknown/malformed id in a URL path still correctly produces a 404
  from the service/repository layer (no int-parse exception is
  possible anymore, since path variables are strings all the way
  through).
- **Scope**: applies to all three id-bearing entities uniformly —
  `Quiz`, `User`, and `QuizCompletion` (including
  `QuizCompletion.quizId`, which references `Quiz.id` and must match
  its type).
- **Dev database**: switching an existing H2 `IDENTITY` integer column
  to a `VARCHAR` UUID column isn't a sane in-place migration for
  disposable local dev data, so the local `webquizdb.mv.db` file was
  deleted and Hibernate recreated the schema fresh via
  `spring.jpa.hibernate.ddl-auto=update` on next boot.

## Domain contracts (detail)

```kotlin
// domain/Quiz.kt
data class Quiz(
    val id: String,
    val title: String,
    val text: String,
    val author: String,
    val options: List<String>,
    val answer: List<Int>,
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

// domain/User.kt
data class User(
    val id: String,
    val email: String,
    val password: String
)

// domain/QuizCompletion.kt
data class QuizCompletion(
    val id: String,
    val quizId: String,
    val userEmail: String,
    val completedAt: OffsetDateTime
)

// domain/response/PagedResult.kt
data class PagedResult<T>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int
)

// domain/response/DeleteResult.kt
sealed class DeleteResult {
    object Deleted : DeleteResult()
    object NotFound : DeleteResult()
    object Forbidden : DeleteResult()
}

// domain/response/AnswerResult.kt
data class AnswerResult(val success: Boolean, val feedback: String)

// domain/repository/QuizRepository.kt
interface QuizRepository {
    fun save(quiz: Quiz): Quiz
    fun findById(id: String): Quiz?
    fun findAll(pageNumber: Int, pageSize: Int): PagedResult<Quiz>
    fun deleteById(id: String)
}

// domain/repository/UserRepository.kt
interface UserRepository {
    fun save(user: User): User
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
}

// domain/repository/QuizCompletionRepository.kt
interface QuizCompletionRepository {
    fun save(completion: QuizCompletion): QuizCompletion
    fun findByUserEmailOrderByCompletedAtDesc(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion>
}

// domain/service/QuizService.kt
interface QuizService {
    fun createQuiz(title: String, text: String, options: List<String>, answer: List<Int>, author: String): Quiz
    fun getQuiz(id: String): Quiz?
    fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz>
    fun solveQuiz(id: String, answer: List<Int>, userEmail: String): AnswerResult?
    fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion>
    fun deleteQuiz(id: String, requesterEmail: String): DeleteResult
}

// domain/service/UserService.kt
interface UserService {
    fun registerUser(email: String, rawPassword: String)
}
```

`QuizService.createQuiz`/`solveQuiz` take plain parameters instead of
a DTO, since domain must not reference controller-layer DTOs. Mapping
from `CreateQuizRequestDto`/`SolveQuizRequestDto` happens inline at the
controller call site.

## Repository adapters (detail)

```kotlin
// repository/dto/QuizDto.kt
@Entity
@Table(name = "quizzes")
class QuizDto(
    @Id var id: String = "",
    @Column(nullable = false) var title: String = "",
    @Column(nullable = false) var text: String = "",
    @Column(nullable = false) var author: String = "",
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_options", joinColumns = [JoinColumn(name = "quiz_id")])
    @Column(name = "option_value", nullable = false)
    @Fetch(FetchMode.SUBSELECT)
    var options: MutableList<String> = mutableListOf(),
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_answers", joinColumns = [JoinColumn(name = "quiz_id")])
    @Column(name = "answer_value", nullable = false)
    @Fetch(FetchMode.SUBSELECT)
    var answer: MutableList<Int> = mutableListOf(),
    @Column(nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now()
)

// repository/mapper/QuizDtoMapper.kt
@Mapper(componentModel = "spring")
interface QuizDtoMapper {
    fun toDomain(dto: QuizDto): Quiz
    fun toDto(domain: Quiz): QuizDto
}

// repository/QuizRepositoryImpl.kt
@Repository
class QuizRepositoryImpl(
    private val entityManager: EntityManager,
    private val mapper: QuizDtoMapper
) : QuizRepository {

    @Transactional
    override fun save(quiz: Quiz): Quiz {
        val dto = mapper.toDto(quiz)
        entityManager.persist(dto)
        return mapper.toDomain(dto)
    }

    override fun findById(id: String): Quiz? =
        entityManager.find(QuizDto::class.java, id)?.let { mapper.toDomain(it) }

    override fun findAll(pageNumber: Int, pageSize: Int): PagedResult<Quiz> {
        val content = entityManager.createQuery(
            "select q from QuizDto q order by q.createdAt", QuizDto::class.java
        )
            .setFirstResult(pageNumber * pageSize)
            .setMaxResults(pageSize)
            .resultList
            .map { mapper.toDomain(it) }

        val totalElements = entityManager.createQuery(
            "select count(q) from QuizDto q", Long::class.java
        ).singleResult

        val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / pageSize + 1).toInt()

        return PagedResult(content, pageNumber, pageSize, totalElements, totalPages)
    }

    @Transactional
    override fun deleteById(id: String) {
        entityManager.find(QuizDto::class.java, id)?.let { entityManager.remove(it) }
    }
}
```

`UserRepositoryImpl` and `QuizCompletionRepositoryImpl` follow the
identical `EntityManager`-injection pattern — `UserRepositoryImpl` for
`save`/`findByEmail`/`existsByEmail`, `QuizCompletionRepositoryImpl`
for `save`/`findByUserEmailOrderByCompletedAtDesc` (content query plus
a separate `count` query for pagination totals, same shape as
`QuizRepositoryImpl.findAll`).

`UserDetailsServiceAdapter` depends on the domain `UserRepository`
(unchanged behavior — loads by email, builds Spring `UserDetails` from
`user.email`/`user.password`).

## Service layer (detail)

```kotlin
// domain/service/QuizServiceImpl.kt
@Service
class QuizServiceImpl(
    private val quizRepository: QuizRepository,
    private val quizCompletionRepository: QuizCompletionRepository
) : QuizService {
    override fun createQuiz(title: String, text: String, options: List<String>, answer: List<Int>, author: String): Quiz =
        quizRepository.save(
            Quiz(id = createId(), title = title, text = text, author = author, options = options, answer = answer)
        )

    override fun getQuiz(id: String): Quiz? = quizRepository.findById(id)

    override fun getAllQuizzes(pageNumber: Int, pageSize: Int): PagedResult<Quiz> =
        quizRepository.findAll(pageNumber, pageSize)

    override fun solveQuiz(id: String, answer: List<Int>, userEmail: String): AnswerResult? {
        val quiz = quizRepository.findById(id) ?: return null
        return if (answer.sorted() == quiz.answer.sorted()) {
            quizCompletionRepository.save(
                QuizCompletion(id = createId(), quizId = id, userEmail = userEmail, completedAt = OffsetDateTime.now())
            )
            AnswerResult(success = true, feedback = "Congratulations, you're right!")
        } else {
            AnswerResult(success = false, feedback = "Wrong answer! Please, try again.")
        }
    }

    override fun getCompletions(userEmail: String, pageNumber: Int, pageSize: Int): PagedResult<QuizCompletion> =
        quizCompletionRepository.findByUserEmailOrderByCompletedAtDesc(userEmail, pageNumber, pageSize)

    override fun deleteQuiz(id: String, requesterEmail: String): DeleteResult {
        val quiz = quizRepository.findById(id) ?: return DeleteResult.NotFound
        if (quiz.author != requesterEmail) return DeleteResult.Forbidden
        quizRepository.deleteById(id)
        return DeleteResult.Deleted
    }
}

// domain/service/UserServiceImpl.kt
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
            User(id = createId(), email = email, password = passwordEncoder.encode(rawPassword)!!)
        )
    }
}
```

Functionally identical to `WebQuizKotlin`'s `QuizService`/`UserService`
— same validation order, same feedback strings, same 403/404 semantics
for delete, aside from the id type change.

## Controller layer (detail)

DTOs shape the HTTP boundary and carry only serialization annotations
(`@JsonFormat` on `QuizCompletionResponseDto.completedAt`, using the
`@param:` use-site target to pin today's behavior against a future
Kotlin default-target change) plus validation annotations
(`@field:NotBlank` etc. on request DTOs) — never persistence or domain
logic.

```kotlin
// controller/mapper/QuizMapper.kt
@Mapper(componentModel = "spring")
interface QuizMapper {
    fun toResponseDto(domain: Quiz): QuizResponseDto
}

// controller/mapper/CompletionMapper.kt
@Mapper(componentModel = "spring")
interface CompletionMapper {
    @Mapping(source = "quizId", target = "id")
    fun toResponseDto(domain: QuizCompletion): QuizCompletionResponseDto
}

// controller/mapper/AnswerMapper.kt
@Mapper(componentModel = "spring")
interface AnswerMapper {
    fun toResponseDto(domain: AnswerResult): AnswerResultDto
}
```

Controllers inject the mapper interfaces (Spring wires the
MapStruct-generated implementation) and call the domain service
interface directly with request fields:

```kotlin
// controller/QuizController.kt (excerpt)
@PostMapping("/api/quizzes")
fun createQuiz(@Valid @RequestBody request: CreateQuizRequestDto, authentication: Authentication): QuizResponseDto {
    val quiz = quizService.createQuiz(
        title = request.title!!,
        text = request.text!!,
        options = request.options!!,
        answer = request.answer ?: emptyList(),
        author = authentication.name
    )
    return quizMapper.toResponseDto(quiz)
}
```

`SecurityConfig` permits `/api/register`, `/actuator/shutdown`,
`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` without auth;
everything else requires HTTP Basic auth. `GlobalExceptionHandler`
converts `MethodArgumentNotValidException` and `DuplicateEmailException`
into `400 Bad Request` with the exception message as the body.

## Build config

- Port: `8892`, H2 file DB: `jdbc:h2:file:../webquizdb`.
- `springdoc-openapi-starter-webmvc-ui:3.1.0` for Swagger UI/OpenAPI
  docs (no `basicAuth` customizer — see the top-level config section
  above).
- `mapstruct:1.6.3` + `mapstruct-processor:1.6.3` via `kapt`, with
  `-java-parameters` added to Kotlin compiler options (required for
  MapStruct's constructor-based mapping to resolve Kotlin data class
  parameter names correctly).

## Testing

- Manual verification after each change: register, create, list
  (checking pagination order), get by id, solve (correct/incorrect),
  completions, delete (owner/non-owner/missing id), wrong-password
  401, Swagger UI reachability — via curl, re-run after every
  structural change in this document's history.
- No automated integration tests beyond the existing
  `WebQuizApplicationTests.contextLoads()`.

## Out of scope

- No new features beyond what `WebQuizKotlin` has today, aside from
  the deliberate id-type change (UUID strings instead of auto-increment
  integers) and its `createdAt`-based ordering consequence.
- No shared code/module between `WebQuiz` and `WebQuizKotlin` — they
  remain fully independent Gradle projects.
