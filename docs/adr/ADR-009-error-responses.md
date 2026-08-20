# ADR-009: Domain exceptions mapped centrally to RFC 7807 responses

## Status
Accepted — 2026-08-10

## Context

Two things go wrong with error handling if left to individual endpoints. Controllers fill with
`try`/`catch` and start returning ad-hoc JSON, so every endpoint has a slightly different error
shape. And status codes get chosen carelessly, which matters because clients act on them: a client
should retry some failures and must not retry others.

There is also an information-disclosure dimension. An error can reveal whether a resource exists,
which is enough to enumerate other users' data.

## Decision

Business code throws meaningful exceptions. A single `@RestControllerAdvice` maps them to status
codes and returns `ProblemDetail` (Spring's RFC 7807 implementation). Controllers contain no
`try`/`catch`.

```json
{ "type": "about:blank", "title": "Unprocessable Entity", "status": 422,
  "detail": "Insufficient funds in account ..." }
```

The mappings, each chosen deliberately:

| Exception | Status | Reasoning |
|---|---|---|
| `ForbiddenException` | 403 | Identity known, action not permitted |
| `InsufficientFundsException` | **422** | Request understood and well-formed; current state forbids it |
| `DuplicateTransactionException` | **409** | Already applied — do not retry, but nothing failed |
| `MissingRequestHeaderException` | **401** | No identity supplied |
| `IllegalArgumentException` | 400 | Malformed input, e.g. a corrupt cursor |

Three of these need justifying.

**422, not 400, for insufficient funds.** 400 means "I could not parse your request" — retrying
without changing it is pointless. 422 means "I understood you and refuse given current state" —
the same request may succeed after a deposit. Clients need to distinguish these.

**409 for a duplicate transaction.** Signals that the operation is already applied, so the caller
must stop retrying but must not treat it as a failure. This is a contract subtlety, called out in
ADR-003 as the reason Stripe returns the cached original response instead.

**401, not 400, for a missing `X-User-Id`.** Spring's default is 400, which is wrong: a missing
identity is an authentication problem. This mapping exists specifically to correct that default —
auth-service originally returned a bare Spring 400 here, which was confusing during manual testing.

Two disclosure rules:

**`ForbiddenException` says only "Access denied".** It must not reveal whether the requested account
exists, or a caller could probe for other users' resources.

**Login failure is deliberately ambiguous.** In auth-service, "no such user" and "wrong password"
both raise the same exception with the same message, producing byte-identical responses. Separate
codes would allow enumeration of registered emails — which for a financial platform is itself
sensitive.

## Alternatives Considered

**`try`/`catch` per controller method.** Rejected: duplicated, easy to forget, and drifts into
inconsistent shapes across endpoints.

**A custom error DTO instead of `ProblemDetail`.** Rejected. `ProblemDetail` implements RFC 7807, an
actual standard, so clients can write one handler rather than learning a bespoke format. No reason
to invent one.

**`@ResponseStatus` on each exception class.** Simpler, and it works — but it couples domain
exceptions to HTTP. `InsufficientFundsException` is thrown by `Account`, which knows nothing about
transport and will also be triggered from Kafka consumers where HTTP status is meaningless. The
mapping belongs at the boundary.

**Returning 200 for a duplicate transaction** (idempotent success). Defensible, and arguably kinder
to callers. Rejected because it hides that nothing happened; 409 with a clear message makes the
retry visible in logs and metrics.

## Consequences

**Positive**
- Consistent error shape across every endpoint, following a published standard
- Status codes carry actionable meaning: retry, do not retry, or fix the request
- Domain exceptions stay free of HTTP concepts, so the same code works from Kafka consumers
- Enumeration defences are implemented in one reviewable place

**Negative**
- Indirection. The status code for a failure is not visible at the throw site — a reader must open
  the handler to know what a client sees.
- `ProblemDetail`'s `title` is derived from the status ("Unprocessable Entity"), which is generic.
  Meaning lives entirely in `detail`.
- Exception messages are returned verbatim to clients. That is intentional for these types, but any
  new exception added to the handler must have its message reviewed for disclosure — the handler is
  a boundary, and treating it casually would leak internals.
- 409-means-success requires documenting for every consumer, and a careless client will treat it as
  an error.
