# ADR-004: Accounts created lazily, trusting the gateway-verified JWT

## Status
Accepted — 2026-08-10

## Context

`accounts.user_id` references a user owned by `auth-service`. Because each service has its own
database (`auth_db`, `wallet_db`), **no foreign key is possible** — Postgres cannot enforce
referential integrity across databases.

So nothing at the schema level prevents an account being created for a `user_id` that does not exist.
The question is what, if anything, should verify it.

## Decision

**Create accounts on first use, trusting the `X-User-Id` header** injected by the API gateway from a
cryptographically verified JWT. No call to `auth-service`, no pre-provisioning.

```java
public Account getOrCreate(UUID userId, String currency) {
    return accounts.findByUserIdAndCurrency(userId, currency)
            .orElseGet(() -> insertOrReadExisting(userId, currency));
}
```

The verified JWT is treated as sufficient evidence that the user exists: the gateway validated an
HMAC signature only it can produce, so a forged `user_id` cannot reach this service through the
normal path.

Concurrent first-use is handled by the database rather than by locking — see Consequences.

## Alternatives Considered

**Synchronous call to `auth-service` to verify the user.** Initially chosen during design, then
rejected on reflection. It makes `wallet-service` unable to function when `auth-service` is down —
availability becomes the product of both services' availability rather than one. It adds latency to
every account creation and requires the full resilience stack (bulkhead, circuit breaker, retry,
timeout) around it.

Decisive argument: **it asks a question we already have the answer to.** The JWT signature is
stronger evidence than a runtime lookup, because it is cryptographic proof that the auth system
issued that identity.

*A service must not need another service to be alive in order to do its own job* — this is what
separates microservices from a distributed monolith.

**Consume a `UserRegistered` Kafka event and pre-create accounts.** Asynchronous, so no availability
coupling, and accounts exist before first use.

*Rejected as unnecessary for validation*, though it remains the right mechanism for a different
purpose: consuming that event to cache `{ userId, email, tier }` would let the wallet render a
statement without calling `auth-service` (event-carried state transfer). Additive later.

Also creates rows for users who never trade — negligible in volume (~20 MB per 100k dormant users),
but it is work done for no benefit.

## Consequences

**Positive**
- **Zero runtime coupling.** `wallet-service` functions with `auth-service` entirely offline.
- No latency, no resilience machinery, no extra failure mode
- Accounts exist only for currencies actually used
- Concurrent first-use is safe: two simultaneous requests both find nothing and both insert, so the
  `UNIQUE (user_id, currency)` constraint rejects the loser, which then reads the winner's committed
  row. Handling rejection rather than attempting to prevent the race removes the window entirely.

**Negative**
- **No referential integrity.** An orphaned account is possible in principle. Accepted because the
  failure is inert: a zero balance with no ledger entries, affecting nothing. This is a deliberate
  choice of *which* failure mode to live with, rather than adding coupling to prevent a harmless one.
- The guarantee rests entirely on the gateway. If the gateway fails to strip a client-supplied
  `X-User-Id` before injecting its own, or if the service is directly reachable, an attacker
  controls this value. **Both are currently true in local development** — the gateway does not exist
  yet and port 8082 is published. Tracked as a prerequisite for the gateway work, not a residual risk.
- Requires the `insertOrReadExisting` recovery path, which is slightly more code than a naive
  `findOrCreate` and must not be "simplified" by a future reader.
