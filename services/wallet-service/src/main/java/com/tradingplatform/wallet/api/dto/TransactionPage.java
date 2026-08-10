package com.tradingplatform.wallet.api.dto;

import com.tradingplatform.wallet.domain.LedgerEntry;

import java.util.List;

/**
 * The cursor is the timestamp of the last returned entry; passing it back fetches the next page.
 * A null cursor means there is nothing more to fetch.
 *
 * <p>No total count is exposed: computing one would require scanning the whole history, which is
 * the cost cursor pagination exists to avoid.
 */
public record TransactionPage(
        List<TransactionResponse> transactions,
        String nextCursor
) {
    public static TransactionPage of(List<LedgerEntry> entries, int requestedSize) {
        List<TransactionResponse> items = entries.stream()
                .map(TransactionResponse::from)
                .toList();

        boolean mayHaveMore = entries.size() == requestedSize;
        String nextCursor = mayHaveMore
                ? Cursor.of(entries.get(entries.size() - 1)).encode()
                : null;

        return new TransactionPage(items, nextCursor);
    }
}
