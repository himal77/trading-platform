# ADR-002: Optimistic locking for balance updates

## Status
Accepted — 2026-08-10

## Context

Two concurrent operations on the same account can lose an update:

```
Thread A: read balance = 100
Thread B: read balance = 100
Thread A: write 100 - 30 = 70
Thread B: write 100 - 50 = 50     ← A's deduction vanished; 30 units created from nothing
```

For a financial ledger this is unacceptable, and it is silent — the row simply holds the wrong
number with no error raised.

Access to a given account row must therefore be serialised. The question is *how*.

## Decision

**Optimistic locking** via a `version` column, mapped with JPA's `@Version`.

```sql
UPDATE accounts SET balance = 70, version = 6
WHERE id = ? AND version = 5;        -- 0 rows updated means someone else won
```

Hibernate appends the `version` predicate to every `UPDATE` automatically and raises
`OptimisticLockException` when no row matches. The caller retries with fresh data.

Backed by a database-level guard that no application bug can bypass:

```sql
CONSTRAINT accounts_balance_non_negative CHECK (balance >= 0)
```

## Alternatives Considered

**Pessimistic locking (`SELECT ... FOR UPDATE`).** Locks the row on read; concurrent transactions
block until commit. Correct, and simpler to reason about.

*Rejected* because a given user's account is rarely contended — different users touch different
rows, so there is almost no concurrency to serialise. Pessimistic locking would pay a lock-and-wait
cost on every operation to protect against a collision that seldom happens, and it introduces
deadlock risk when a transaction touches two accounts (the EUR and BTC legs of a trade) in
inconsistent order.

Worth revisiting for genuinely hot rows — a platform treasury account touched by every trade would
invert this trade-off, since under high contention optimistic locking degenerates into a retry
storm.

**`SERIALIZABLE` isolation.** Postgres would detect the conflict and abort one transaction. Rejected
as heavier than needed: it applies to every statement in the transaction rather than the one row
that matters, and it still requires retry handling — so it is strictly more expensive than
`@Version` for the same outcome.

**Atomic SQL (`UPDATE accounts SET balance = balance - 30 WHERE id = ?`).** No read-then-write, so
no lost update at all. Rejected because the business rule (`is the balance sufficient?`) and the
ledger's `balance_after` value both require knowing the resulting balance in application code.
`CHECK (balance >= 0)` gives us this guarantee anyway as a backstop.

## Consequences

**Positive**
- No locks held, so no lock contention and no deadlocks between the two legs of a trade
- One annotation; Hibernate generates the guard on every `UPDATE` with no chance of a developer
  forgetting it on a new code path
- `CHECK (balance >= 0)` makes an overdraft physically impossible even if the Java check is bypassed
- Fast in the common case, which is the overwhelming majority of operations

**Negative**
- **Callers must handle `OptimisticLockException` and retry.** Nothing currently does — a concurrent
  conflict would surface to the client as a 500. This is a real gap; retry belongs in `WalletService`
  or in the saga orchestrator once `trade-service` exists.
- **Untested.** The mechanism is wired but no test races two threads against one account, so this
  ADR asserts a property that is currently unproven. Standard Hibernate behaviour, but "standard"
  is not "verified".
- Under high contention this degrades badly (repeated retries); if a hot shared account is ever
  introduced, this decision must be reconsidered rather than inherited.
