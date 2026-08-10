package com.tradingplatform.wallet.api;

import com.tradingplatform.wallet.api.dto.BalanceResponse;
import com.tradingplatform.wallet.api.dto.Cursor;
import com.tradingplatform.wallet.api.dto.TransactionPage;
import com.tradingplatform.wallet.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/{userId}")
    public List<BalanceResponse> getBalances(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") UUID authenticatedUserId) {

        requireOwnership(authenticatedUserId, userId);

        return walletService.findAccounts(userId).stream()
                .map(BalanceResponse::from)
                .toList();
    }

    @GetMapping("/{userId}/transactions")
    public TransactionPage getTransactions(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") UUID authenticatedUserId,
            @RequestParam(defaultValue = "EUR") String currency,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {

        requireOwnership(authenticatedUserId, userId);

        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);

        return TransactionPage.of(
                walletService.findTransactions(userId, currency, decoded, pageSize), pageSize);
    }

    /**
     * Guards against BOLA: the path variable is caller-supplied and untrusted, while X-User-Id is
     * set by the gateway from a verified JWT. Authentication alone is not enough — a logged-in user
     * must not be able to read another user's wallet by changing the URL.
     */
    private void requireOwnership(UUID authenticatedUserId, UUID requestedUserId) {
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new ForbiddenException();
        }
    }
}
