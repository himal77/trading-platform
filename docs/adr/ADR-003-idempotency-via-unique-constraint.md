# ADR-003: Idempotency enforced by a unique index, not application logic

## Status
Accepted — 2026-08-10

## Context

Every network call between services can be retried — by a client, a Kafka consumer redelivering a
message, or a saga step re-executing after a timeout. A retried credit that applies twice creates
money; a retried debit destroys it.

The caller therefore supplies a `transaction_id` identifying the *logical operation*, and posting
the same operation twice must be impossible.

```
POST credit, transaction_id = txn-abc  →  +620
POST credit, transaction_id = txn-abc  →  must NOT apply again
```

Note the operation is not naturally unique on `transaction_id` alone: one trade legitimately posts
to two accounts (EUR out, BTC in) under the same transaction.

## Decision

A **unique index on `(transaction_id, account_id)`**, with the resulting violation translated into
a domain exception:

```sql
CREATE UNIQUE INDEX idx_ledger_transaction_account
    ON ledger_entries (transaction_id, account_id);
```

```java
try {
    return ledger.saveAndFlush(LedgerEntry.post(...));
} catch (DataIntegrityViolationException e) {
    throw new DuplicateTransactionException(transactionId, account.getId());
}
```

`saveAndFlush` rather than `save` is deliberate: the SQL must execute inside the `try` block, since
a deferred flush would raise the violation at commit time, outside any handler.

The index also serves lookups by `transaction_id` alone via the left-prefix rule, so no separate
index is needed.

## Alternatives Considered

**Check before inserting** (`if (ledger.existsByTransactionIdAndAccountId(...)) return;`). Rejected:
check-then-act has a window between the two statements in which a concurrent request passes the same
check. Both then insert. The race is narrow but real, and this is precisely the class of bug that
must not exist in a ledger.

**A dedicated `idempotency_keys` table storing key → cached response.** This is what Stripe does, and
it is strictly better for a *public* API: a retrying client receives the original response rather
than an error, so the retry is transparent.

*Rejected for now* because the consumers are internal services, which can act on
`DuplicateTransactionException` directly — they know the operation already succeeded. Worth adopting
if the wallet is ever exposed to external callers.

**Distributed lock (Redis) around the operation.** Rejected as strictly worse: it adds a network
dependency and a failure mode (lock held by a crashed process) to solve something the database
already guarantees for free.

## Consequences

**Positive**
- **Unbypassable.** No application bug, new code path, or direct SQL can double-post. The guarantee
  lives in the schema, not in a convention.
- No extra table, no extra query, no lock — one index doing two jobs (uniqueness and lookup)
- Correct under concurrency by construction, with no window to reason about

**Negative**
- **The caller gets an error, not the original result.** `DuplicateTransactionException` maps to 409,
  so a retrying client must interpret 409 as "already applied, treat as success" rather than as a
  failure. That is a contract subtlety which must be documented for every consumer — and it is the
  precise reason Stripe chose the cached-response approach instead.
- Detecting a duplicate costs a failed `INSERT` and a rolled-back transaction. Negligible at this
  volume, but it is not free.
- Relies on translating a vendor-agnostic `DataIntegrityViolationException`, which is raised for
  *any* constraint violation on that statement. Today only one constraint can realistically fire
  here; if another is added to `ledger_entries`, this handler would misreport it and must be
  narrowed to inspect the constraint name.
