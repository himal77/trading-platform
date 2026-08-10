# Architecture Decision Records

Each record captures one significant decision: the context that forced it, what was chosen, what
was rejected and why, and the consequences — including the ones that hurt.

An ADR that lists only benefits is not a record of a decision, it is marketing. Where a decision
carries a real cost or a known gap, it is stated in the Consequences section.

## Records

### Data model and correctness
| # | Decision | Status |
|---|---|---|
| [001](ADR-001-double-entry-ledger-scope.md) | Ledger records only the user's side of a trade | Accepted |
| [002](ADR-002-optimistic-locking-for-balances.md) | Optimistic locking for balance updates | Accepted |
| [003](ADR-003-idempotency-via-unique-constraint.md) | Idempotency enforced by a unique index | Accepted |
| [008](ADR-008-atomic-writes-over-check-then-act.md) | Atomic writes instead of check-then-act | Accepted |

### Service boundaries
| # | Decision | Status |
|---|---|---|
| [004](ADR-004-lazy-account-creation.md) | Accounts created lazily, trusting the verified JWT | Accepted |

### API design
| # | Decision | Status |
|---|---|---|
| [005](ADR-005-opaque-composite-cursor-pagination.md) | Opaque composite cursor pagination | Accepted |
| [007](ADR-007-entities-never-cross-the-http-boundary.md) | Entities never cross the HTTP boundary | Accepted |
| [009](ADR-009-error-responses.md) | Domain exceptions mapped centrally to RFC 7807 | Accepted |

### Code structure
| # | Decision | Status |
|---|---|---|
| [006](ADR-006-domain-objects-enforce-their-own-invariants.md) | Domain objects enforce their own invariants | Accepted |

## Open items raised by these records

Gaps identified while writing the ADRs above, tracked here rather than left implicit:

- **ADR-002** — no caller handles `OptimisticLockException`; a concurrent conflict currently
  surfaces as a 500. Retry belongs in `WalletService` or the saga orchestrator.
- **ADR-002** — the optimistic lock is untested; no test races two threads on one account.
- **ADR-003** — the `DataIntegrityViolationException` handler assumes the violation is the
  idempotency index. It would mislabel any other constraint failure on that insert, so it should
  inspect the constraint name.
- **ADR-004** — the trust model requires the gateway to strip client-supplied `X-User-Id` and the
  service not to be publicly reachable. Neither holds yet in local development.
