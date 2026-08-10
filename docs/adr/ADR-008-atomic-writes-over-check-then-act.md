# ADR-008: Atomic writes instead of check-then-act

## Status
Accepted — 2026-08-10

## Context

Three separate problems in this codebase have the same shape:

```
Is this refresh token still valid?      → then consume it
Does this account already exist?        → then create it
Has this transaction been posted?       → then post it
```

Written as a check followed by an action, each has a window between the two statements:

```java
if (!exists(key)) {        // ← Thread B also reaches here and also sees "no"
    insert(key);           // ← both insert
}
```

The window is microseconds, which means it is rare, which means it will not appear in testing and
will appear in production. For a ledger, "rare" is not a defence — a double-credit is a permanent
accounting error.

## Decision

Never check first. Attempt the write, let the storage engine reject it, and handle the rejection.

| Problem | Mechanism |
|---|---|
| Consume a refresh token exactly once | Redis `GETDEL` (a single atomic command) |
| One account per (user, currency) | `UNIQUE (user_id, currency)`, recover by reading the winner's row |
| Post a transaction once per account | `UNIQUE (transaction_id, account_id)`, translate to a domain error |
| Never overwrite a concurrent balance change | `@Version` — `UPDATE ... WHERE version = ?` |
| Never allow a negative balance | `CHECK (balance >= 0)` |

The account case shows the shape most clearly — the race is expected, not prevented:

```java
try {
    return accounts.saveAndFlush(Account.open(userId, currency));
} catch (DataIntegrityViolationException e) {
    return accounts.findByUserIdAndCurrency(userId, currency).orElseThrow(() -> e);
}
```

Two supporting details:

**`saveAndFlush`, not `save`.** JPA may defer the `INSERT` until commit, which would raise the
violation outside the `try` block where nothing can handle it. The SQL must execute where the
handler is.

**`.orElseThrow(() -> e)`** — if the insert failed *and* the row is still absent, the violation was
not the race we assumed. Rethrowing the original avoids swallowing an unrelated failure.

## Alternatives Considered

**Synchronised blocks or a JVM lock.** Rejected outright: worthless across multiple instances, which
is the deployment model.

**Distributed lock in Redis.** Rejected as strictly worse than a constraint. It adds a network
dependency, a new failure mode (lock held by a crashed process), and TTL tuning, to achieve something
the database already guarantees.

**`SERIALIZABLE` isolation everywhere.** Would work, and Postgres would detect the conflicts.
Rejected as a blunt instrument: it applies to every statement rather than the one row that matters,
and retry handling is still required. See ADR-002.

**Accept the risk given how narrow the window is.** Rejected. The consequence is a silent accounting
error with no detection path, and the correct fix costs one index.

## Consequences

**Positive**
- Correct by construction. No window exists to reason about, so no test is needed to prove one is
  absent.
- Enforced by the storage engine, so no future code path, direct SQL, or migration can bypass it.
- No locks, so no lock contention and no deadlocks.
- The same reasoning applies uniformly to Redis and Postgres.

**Negative**
- Rejection handling looks like defensive noise to a reader who has not thought about concurrency.
  Each site carries a comment explaining the race it recovers from, precisely so it is not
  "simplified" away.
- Detecting a duplicate costs a failed statement and a rolled-back transaction. Cheap here, but not
  free, and it would matter under heavy contention.
- Depends on constraint violations being translated correctly. `DataIntegrityViolationException` is
  raised for *any* violation on the statement, so a handler that assumes a specific cause becomes
  wrong the moment another constraint is added to the table. This is currently a known defect in
  `WalletService.post` — see the open items in the ADR index.
