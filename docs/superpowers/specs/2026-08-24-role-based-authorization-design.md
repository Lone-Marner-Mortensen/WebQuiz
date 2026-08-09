# Role-Based Authorization Design

## Context

The current authorization model is binary: an endpoint either requires
any authenticated user (`anyRequest().authenticated()`) or is fully
public (`permitAll()`), plus one hand-rolled ownership check on quiz
deletion (`quiz.author != requesterEmail`). There is no concept of
roles — `UserDetailsServiceAdapter` grants every user an empty
authorities list, so Spring Security has nothing to check `hasRole`
against even if we wanted to use it.

This design introduces an `ADMIN` role and reworks the access rules
so that:
- Browsing and solving quizzes is public (no login).
- Creating a quiz and viewing your own completions requires login.
- Deleting a quiz requires being the quiz's author **or** an admin.
- A new `/api/admin/**` namespace is reserved for admin-only actions,
  gated by Spring Security's role matcher, starting with a
  promote-user-to-admin endpoint.

## Goals

- Add a `role` (`USER` | `ADMIN`) to the `User` domain, persisted via JPA.
- Bridge that role into Spring Security authorities so
  `hasRole("ADMIN")` works in the filter chain.
- Change the access matrix per endpoint (see table below).
- Move the "author or admin" decision into the domain service layer,
  since it is a business rule, not a URL-shape rule.
- Provide a bootstrap admin account seeded from environment variables
  on startup (create-if-missing, idempotent).
- Add an admin-only endpoint to promote another registered user to
  `ADMIN`.
- Self-registration (`POST /api/register`) always creates `Role.USER`
  — admin can never be granted through that path.

## Non-goals

- No fine-grained permission/authority system beyond the two roles
  (`USER`, `ADMIN`). No `hasAuthority("API_ACCESS")`-style custom
  authorities — two roles cover every rule in this design.
- No demotion endpoint, no way to list/browse all users, no audit
  log of role changes. Out of scope for this pass.
- No change to HTTP Basic as the auth mechanism.
- No UI/frontend work.

## Access matrix (target state)

| Endpoint | Method | Access |
|---|---|---|
| `/api/register` | POST | public |
| `/api/quizzes` | GET | public |
| `/api/quizzes/{id}` | GET | public |
| `/api/quizzes/{id}/solve` | POST | public |
| `/api/quizzes/completed` | GET | authenticated (any role) |
| `/api/quizzes` | POST | authenticated (any role) |
| `/api/quizzes/{id}` | DELETE | authenticated; author of the quiz OR `ADMIN` |
| `/api/admin/**` | POST | `ADMIN` only |
| `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/shutdown` | — | public (unchanged) |

## Data model

- New enum `quiz.domain.Role { USER, ADMIN }`.
- `quiz.domain.User` gains `val role: Role = Role.USER`.
- `quiz.repository.dto.UserDto` (JPA entity) gains
  `@Column(nullable = false) var role: String`, storing `Role.name`.
  Kept as `var` (like the existing `password` field) since the
  promotion endpoint needs to mutate it in place.
- `UserDtoMapper` (MapStruct) picks up the new field automatically;
  enum-to-string mapping uses MapStruct's default `String <-> Enum`
  conversion (by name).
- `UserRepository` gains no new method signatures for reads; the
  promotion flow reuses `findByEmail` plus a new `save`-based update
  (the interface already exposes `save(user: User): User`, which is
  sufficient — promotion becomes "load user, copy with new role, save").

## Authentication → authorities bridge

`UserDetailsServiceAdapter.loadUserByUsername` currently builds
authorities as `emptyList()`. Change to:

```kotlin
.authorities(listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")))
```

Spring Security's `hasRole("X")` checks for authority `"ROLE_X"` by
convention — this is what makes `hasRole("ADMIN")` in the filter chain
possible.

## Security filter chain

`SecurityConfig.securityFilterChain` changes from a single
`permitAll()` list + catch-all, to method-aware matchers:

```kotlin
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
```

Notes:
- `GET /api/quizzes/*` covers `GET /api/quizzes/{id}`; it does not
  match `/api/quizzes/completed` because that is also `GET
  /api/quizzes/completed` — a distinct literal path already excluded
  by not being in this matcher's pattern set. Method-level rules are
  evaluated top-to-bottom, first match wins, so ordering these before
  the generic `/api/admin/**` and catch-all matters.
- `/api/admin/**` is method-scoped to POST for now since the only
  admin action defined here is promotion (POST). Future admin GET
  endpoints (e.g. listing users) would need their own matcher line —
  intentionally left out per non-goals.
- Path-based matchers can only gate whole URL shapes; they cannot
  express "unless you own this specific row." That is why the
  author-or-admin rule for quiz deletion is NOT expressed here — see
  next section.

## Author-or-admin authorization for delete

`QuizServiceImpl.deleteQuiz(id, requesterEmail)` currently rejects
based only on `quiz.author != requesterEmail`. This is a per-resource
decision that depends on data (who owns this quiz, what role does
this requester have) that a URL matcher cannot see — it must live in
application code that runs after Spring Security has already let the
authenticated request through.

**Decision: the service looks up the requester's role itself**, rather
than the controller computing an `isAdmin` boolean and passing it down.
Rationale: this is a business rule ("who may delete a quiz"), and
business rules belong in the domain/service layer per the existing
hexagonal architecture. Keeping it in the service means every future
admin-gated business rule (e.g. "admin may edit any quiz") reuses the
same lookup instead of each controller re-deriving and threading an
`isAdmin` flag through its own method signature. It also mirrors the
existing pattern in `UserDetailsServiceAdapter`, which already loads a
`User` by email to make an authorization-adjacent decision.

Concretely:
- `QuizServiceImpl` gains a `UserRepository` dependency (already
  exists as an interface, just not currently injected into
  `QuizServiceImpl`).
- `deleteQuiz` becomes:
  ```kotlin
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
  ```
- `QuizController.deleteQuiz` is unchanged — it already passes only
  `authentication.name`.

## Registration

`UserServiceImpl.registerUser` is unchanged in signature; it
constructs `User(..., role = Role.USER)` explicitly. There is no code
path from `/api/register` that can produce an `ADMIN` user.

## Bootstrap admin

A new `@Component` (e.g. `quiz.security.AdminBootstrapper`)
implementing Spring Boot's `ApplicationRunner`:

- Reads `admin.email` / `admin.password` from
  `application.properties`, which default to
  `${ADMIN_EMAIL}` / `${ADMIN_PASSWORD}` env var placeholders.
- On startup, if `userRepository.existsByEmail(adminEmail)` is
  `false`, creates a `User` with `role = Role.ADMIN` and the
  BCrypt-encoded password via the existing `PasswordEncoder` bean.
- Idempotent: safe to run on every app restart, does nothing once the
  admin account exists.
- If the env vars are unset, `application.properties` provides local
  development defaults (documented in-file, not committed as
  production secrets).

## Promotion endpoint

- `UserService` gains `fun promoteToAdmin(email: String)`.
- `UserServiceImpl.promoteToAdmin` loads the user by email (404 /
  not-found handling mirrors existing patterns — throwing a
  not-found-style exception the `GlobalExceptionHandler` already
  maps, consistent with how `DuplicateEmailException` is handled
  today), copies it with `role = Role.ADMIN`, and saves it.
- New endpoint: `POST /api/admin/users/{email}/promote` in a new
  `AdminController` (`quiz.controller.AdminController`), calling
  `userService.promoteToAdmin(email)`.
- Reachability is enforced entirely by the filter chain's
  `hasRole("ADMIN")` rule on `/api/admin/**` — the controller itself
  does not need to re-check the role.

## Testing

- Unit tests for `QuizServiceImpl.deleteQuiz`: author-can-delete,
  admin-can-delete-others-quiz, non-author-non-admin-forbidden,
  quiz-not-found.
- Unit test for `UserServiceImpl.registerUser`: always produces
  `Role.USER` regardless of input.
- Unit test for `AdminBootstrapper`: creates admin when absent,
  no-ops when already present.
- Integration test (`@SpringBootTest` + `MockMvc` or
  `TestRestTemplate`) for the filter chain: verify the access matrix
  table above end-to-end — public endpoints reachable without auth,
  `/api/quizzes` POST rejected without auth, `/api/admin/**` rejected
  for a non-admin authenticated user and accepted for an admin.
- Integration test for the promotion endpoint: non-admin gets 403,
  admin gets 200 and the promoted user can subsequently access
  admin-only routes.

## Open questions / risks

- `GlobalExceptionHandler`'s existing not-found handling is assumed
  sufficient for "promote a nonexistent email" — confirm during
  implementation that reusing the existing exception type (or adding
  a small new one) is consistent with current error-handling
  conventions rather than introducing a parallel style.
