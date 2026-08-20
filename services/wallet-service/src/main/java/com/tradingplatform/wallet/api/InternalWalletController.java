package com.tradingplatform.wallet.api;

import com.tradingplatform.wallet.api.dto.LedgerPostingRequest;
import com.tradingplatform.wallet.api.dto.TransactionResponse;
import com.tradingplatform.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only. These endpoints move money, so they must never be reachable by an end
 * user — a customer able to call /credit could mint themselves funds.
 *
 * <p>Two things keep them internal: the API gateway defines no route for {@code /internal/**}, and
 * in production a NetworkPolicy restricts the port to in-cluster callers. Consequently there is no
 * BOLA check here: the caller is another service acting on the system's behalf, not a user whose
 * ownership of the account could be verified.
 */
@RestController
@RequestMapping("/internal/wallets")
public class InternalWalletController {

    private final WalletService walletService;

    public InternalWalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/{userId}/credit")
    public TransactionResponse credit(@PathVariable UUID userId,
                                      @Valid @RequestBody LedgerPostingRequest request) {
        return TransactionResponse.from(walletService.credit(
                request.transactionId(), userId, request.currency(),
                request.amount(), request.entryType()));
    }

    @PostMapping("/{userId}/debit")
    public TransactionResponse debit(@PathVariable UUID userId,
                                     @Valid @RequestBody LedgerPostingRequest request) {
        return TransactionResponse.from(walletService.debit(
                request.transactionId(), userId, request.currency(),
                request.amount(), request.entryType()));
    }
}
