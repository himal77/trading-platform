# ADR-005: Opaque composite cursor pagination for transaction history

## Status
Accepted — 2026-08-10

## Context

Transaction history grows without bound and is written continuously. Two properties are required:
paging must stay fast at any depth, and no entry may ever be skipped or duplicated — a missing
transaction in a financial statement destroys trust in the whole system.

`OFFSET` fails both:

```sql
SELECT * FROM ledger_entries ORDER BY created_at DESC OFFSET 100000 LIMIT 20;
-- reads 100,020 index entries, discards 100,000
```

It is also unstable under concurrent inserts: a new entry at the top shifts every subsequent row, so
the reader sees a row twice or misses one entirely at the page boundary.

## Decision

**Keyset (cursor) pagination seeking on `(created_at, id)`**, exposed to clients as a single opaque
base64 string.

```sql
WHERE account_id = ?
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 20
```

Supported by an index whose column order and direction match the query exactly, so no sort step is
required:

```sql
CREATE INDEX idx_ledger_account_created
    ON ledger_entries (account_id, created_at DESC, id DESC);
```

The `id` component is not decoration. Ledger entries can share a `created_at` — trades occur
microseconds apart — and comparing on the timestamp alone would skip or repeat every entry in a tied
group at a page boundary. `(created_at, id)` is strictly ordered, making paging exact.

The cursor is encoded so clients treat it as a token:

```json
{ "transactions": [...], "nextCursor": "MjAyNi0wOC0wMVQxMDoxNTowMFp8YTFiMi4uLg" }
```

`nextCursor: null` signals the end of the history.

## Alternatives Considered

**`OFFSET`/`LIMIT`.** Rejected on both counts above: O(n) at depth, and unstable under concurrent
inserts. Acceptable only for small, static datasets.

**Cursor on `created_at` alone.** Simpler, and was the initial implementation. Rejected once the tie
case was considered: entries sharing a timestamp are silently dropped or duplicated. For financial
history this is not an acceptable edge case.

**Exposing the cursor as a raw timestamp.** Rejected because clients would parse and construct it,
which freezes the encoding forever. Base64 discourages inspection, so `(created_at, id)` can become
`(created_at, id, shard)` without breaking a single client.

**Returning a total count.** Rejected: `COUNT(*)` scans every entry the account ever had — exactly
the cost this decision exists to avoid. Reintroducing it in a different field would defeat the
design.

## Consequences

**Positive**
- Constant cost at any depth; page 50,000 is as cheap as page 1
- Exact: no entry can be skipped or repeated, even with identical timestamps or concurrent inserts
- Index-only traversal with no sort, since index order matches `ORDER BY` including `DESC`
- The encoding can evolve freely because clients only echo the token back

**Negative**
- **No total count and no random page access.** A client cannot jump to "page 7" or render "showing
  20 of 4,312". This is a genuine UX limitation, not a detail: infinite scroll works, numbered
  pagination does not.
- `nextCursor` is derived from `entries.size() == requestedSize`, a heuristic. Requesting 20 and
  receiving exactly 20 returns a cursor even when the history ends there, so the client makes one
  wasted request returning an empty page. Accepted over fetching `limit + 1` for simplicity; the
  alternative is a known refinement.
- Base64 is **obfuscation, not security** — trivially decodable. Nothing sensitive may ever be placed
  in a cursor.
- Requires explicit JPQL rather than a derived query name, because JPQL has no row-comparison
  operator; the `(a, b) < (x, y)` semantics are expanded manually and must stay consistent with the
  index.
