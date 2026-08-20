# ADR-007: Entities never cross the HTTP boundary

## Status
Accepted — 2026-08-10

## Context

Returning a JPA entity from a controller is the shortest path to a working endpoint. It also means
the wire format is whatever the database schema happens to be.

Serialising `Account` directly would publish `id`, `userId` and `version`. The optimistic-lock
counter is meaningless to a client, and the internal account UUID invites clients to start using it
as an identifier — after which it can never change.

There is a second problem specific to JPA: serialising a lazily-loaded association triggers a query
during response writing, outside the transaction. That surfaces either as a
`LazyInitializationException` or as an unexpected N+1.

## Decision

Every HTTP response is a `record` DTO with an explicit mapping from the entity.

```java
public record BalanceResponse(String currency, BigDecimal balance) {
    public static BalanceResponse from(Account account) { ... }
}
```

The mapping is written by hand, so adding a column to a table does not change the API. Exposure is
opt-in rather than opt-out.

Records specifically: immutable, no setters, value semantics, no boilerplate.

Two consequences worth stating explicitly, because they are decisions rather than side effects:

**`TokenResponse` in auth-service has no refresh-token field at all.** The refresh token travels only
as an `HttpOnly` cookie. Omitting the field makes leaking it into the body structurally impossible,
rather than something a reviewer must catch.

**`TransactionPage` exposes no total count.** See ADR-005 — a count would require scanning the whole
history.

## Alternatives Considered

**Return entities, hide fields with `@JsonIgnore`.** Rejected because it is opt-out: a new column is
exposed by default, and forgetting one annotation leaks it. The failure mode is silent and only
discovered by inspecting the response. DTOs invert this — a new column is invisible until someone
deliberately maps it.

**Return entities, use `@JsonView` per endpoint.** Rejected as harder to read than plain DTOs: the
shape of a response becomes a function of annotations scattered across the entity, so no single file
tells you what an endpoint returns.

**MapStruct or similar to generate mappers.** Reasonable at scale. Rejected here as premature — the
DTOs have two to five fields and a hand-written `from()` is shorter than the configuration needed to
generate it. Worth revisiting if mapping code grows.

## Consequences

**Positive**
- Schema changes cannot silently alter the public API
- Internal identifiers and the `version` counter are not published, so clients cannot couple to them
- No lazy-loading during serialisation
- Response shape is readable in one file per endpoint
- Structurally prevents specific leaks, notably the refresh token

**Negative**
- Boilerplate. Every response type is an extra file plus a mapping method, and adding a field means
  editing two places. This is the cost of the guarantee.
- Two representations of the same concept can drift, though the compiler catches most of it
- Nested objects would need nested DTOs, which does not scale gracefully. Not an issue at present;
  would push toward MapStruct or a query-projection approach.
