# ADR-006: Domain objects enforce their own invariants

## Status
Accepted — 2026-08-10

## Context

The usual JPA entity has a public no-arg constructor and a setter per field. Any caller can then
construct a half-initialised object, or assign a balance directly:

```java
Account a = new Account();
a.setBalance(new BigDecimal("999999"));   // no ledger entry, no validation, no history
```

For a ledger this is the whole failure mode we are trying to prevent. If arbitrary code can assign
a balance, none of the invariants in ADR-001 or ADR-002 hold.

## Decision

Entities expose no setters. Construction goes through a static factory, and state changes go through
methods that enforce their own rules.

```java
protected Account() { }                          // JPA requires it; nothing else can call it

public static Account open(UUID userId, String currency) { ... }   // the only way to build one

public void credit(BigDecimal amount) { requirePositive(amount); ... }
public void debit(BigDecimal amount)  { requirePositive(amount); if (insufficient) throw ...; }
```

Three supporting rules:

**Callers pass positive magnitudes. Direction comes from the method name.** `debit(-100)` is
rejected rather than quietly behaving as a credit. Sign ambiguity in ledger code is a common source
of bugs, so the API removes the possibility.

**Immutable columns are marked `updatable = false`.** `userId`, `currency` and `createdAt` on
`Account`; every column on `LedgerEntry`. Hibernate then has nothing it can put in an `UPDATE`, so
`LedgerEntry` is append-only by construction rather than by convention.

**`LedgerEntry.post()` takes the `Account`, not a balance figure.** It reads `getBalance()` itself,
which forces the correct call order:

```java
account.debit(amount);                     // balance changes first
LedgerEntry.post(txnId, account, ...);     // entry captures the new balance
```

Passing a number would make it possible to record `balance_after` from before the change. Passing
the object makes the wrong order awkward to write.

## Alternatives Considered

**Setters plus validation in the service layer.** Rejected. The check then lives in whichever
service remembered to call it. A new code path, a Kafka consumer, or a test fixture bypasses it. The
invariant belongs where the state lives.

**Bean Validation annotations (`@Min`, `@DecimalMin`) on entity fields.** Rejected as insufficient:
they cannot express "the debit must not exceed the current balance", which depends on other state.
They also fire at flush time rather than at the point of the mistake, so the stack trace is far from
the cause.

**Lombok `@Data` / `@Setter`.** Rejected. It generates exactly the setters this ADR exists to
prevent.

## Consequences

**Positive**
- An `Account` cannot exist in an invalid state. There is no code path that produces one.
- `LedgerEntry` immutability is enforced by the ORM mapping, not by reviewers noticing.
- Business rules sit next to the data they constrain, so reading `Account` tells you the rules.
- `balance_after` cannot record a stale balance.

**Negative**
- Fights the framework in places. JPA and Jackson both expect mutable beans, so anything that wants
  to deserialise directly into an entity will not work — one reason DTOs are mandatory (ADR-007).
- The `protected` no-arg constructor exists purely for Hibernate and looks like dead code. A future
  reader may try to remove it; the comment in the source explains why it stays.
- Static factories are less discoverable than `new`. Someone unfamiliar with the codebase has to
  find `Account.open` rather than being led there by the constructor.
