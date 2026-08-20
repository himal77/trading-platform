# ADR-001: Ledger records only the user's side of a trade

## Status
Accepted — 2026-08-10

## Context

The wallet needs an auditable record of every balance movement. A single mutable `balance` column
is insufficient: an `UPDATE` destroys history, so there is no way to answer *why* a balance is what
it is, and a lost update caused by a concurrency bug would be silent and undetectable.

Double-entry accounting solves this by recording movements as immutable entries rather than
mutating a total. The question was how far to take it, given that a trade crosses currencies:

```
User buys 0.01 BTC for EUR 620

Entry 1: EUR account   -620.00 EUR
Entry 2: BTC account     +0.01 BTC
```

These cannot be summed. EUR and BTC are different units, so there is no arithmetic under which
this transaction "balances to zero" — the classic double-entry invariant does not apply.

## Decision

Record **two entries per trade — the user's side only.** The exchange's counterparty accounts are
not modelled.

The invariant asserted is **reconciliation per account**, not balancing per transaction:

```sql
-- must hold for every account, always
SELECT a.id FROM accounts a
JOIN ledger_entries l ON l.account_id = a.id
GROUP BY a.id, a.balance
HAVING a.balance <> SUM(l.amount);      -- must return zero rows
```

`accounts.balance` is a cached running total; `ledger_entries` is the source of truth. Both are
written inside one transaction, so the cached value can always be rebuilt by replaying the ledger.

## Alternatives Considered

**Four entries per trade, with exchange counterparty accounts.** Models the trade as two transfers
between the user and the exchange, so each currency balances independently:

```
① user EUR      -620.00 EUR
② exchange EUR  +620.00 EUR    → EUR sums to 0
③ exchange BTC    -0.01 BTC
④ user BTC        +0.01 BTC    → BTC sums to 0
```

This is what real exchanges do. It yields a genuine per-transaction invariant
(`SUM(amount) GROUP BY transaction_id, currency = 0`), and makes "how much EUR does the platform
hold?" answerable from the same table.

*Rejected for now* as unnecessary complexity for a platform with no counterparty settlement, no
platform treasury reporting, and no external audit requirement. The reconciliation invariant
already catches the failure mode we actually care about — balance drift from concurrent writes.

**Single mutable `balance` column, no ledger.** Rejected: an `UPDATE` destroys history, corrections
are indistinguishable from fraud, and a lost update fails silently with nothing to compare against.

**Two columns (`debit`, `credit`) instead of one signed `amount`.** Initially chosen for
readability, then rejected: every balance calculation becomes
`SUM(COALESCE(credit,0) - COALESCE(debit,0))`, and a `CHECK` constraint is needed to prevent both
columns being populated at once — a bug class that cannot exist with one signed column. Direction
is already legible from `entry_type`. Signed amount is what Stripe, Modern Treasury, and
TigerBeetle use.

## Consequences

**Positive**
- Full immutable history; corrections are posted as `REVERSAL` entries, so the error and its fix
  both remain visible
- Balance drift is detectable by replaying the ledger — the failure a single mutable column hides
- `SUM(amount)` is a trivial one-column aggregate
- Moving to the four-entry model later is purely **additive**: more rows per transaction, no schema
  change, no migration of existing entries

**Negative**
- **"Double-entry" is partly aspirational.** There is no per-transaction balancing invariant, so a
  reviewer expecting classical double-entry will find this incomplete. Stated plainly here so it is
  not mistaken for an oversight.
- The platform's own holdings cannot be derived from the ledger; that requires the counterparty
  accounts
- `accounts.balance` is denormalised, so it can in principle diverge. Mitigated by writing both in
  one transaction, optimistic locking, and the reconciliation assertion in tests — but the
  denormalisation is a real cost accepted for read performance.
