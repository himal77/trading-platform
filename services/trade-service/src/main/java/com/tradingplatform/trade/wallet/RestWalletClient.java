package com.tradingplatform.trade.wallet;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Resilience annotations apply outermost-first: bulkhead caps concurrent calls so a hanging wallet
 * cannot exhaust the service's threads, the circuit breaker fails fast once the wallet is
 * persistently down, and retry covers transient blips. The read timeout is configured on the
 * RestClient itself.
 *
 * <p>Retrying is only safe because the wallet enforces idempotency on the transaction id.
 */
@Component
class RestWalletClient implements WalletClient {

    private final RestClient restClient;

    RestWalletClient(RestClient walletRestClient) {
        this.restClient = walletRestClient;
    }

    @Override
    @Bulkhead(name = "wallet")
    @CircuitBreaker(name = "wallet")
    @Retry(name = "wallet")
    public void debit(UUID transactionId, UUID userId, String currency,
                      BigDecimal amount, String entryType) {
        post("/internal/wallets/%s/debit".formatted(userId),
                transactionId, currency, amount, entryType);
    }

    @Override
    @Bulkhead(name = "wallet")
    @CircuitBreaker(name = "wallet")
    @Retry(name = "wallet")
    public void credit(UUID transactionId, UUID userId, String currency,
                       BigDecimal amount, String entryType) {
        post("/internal/wallets/%s/credit".formatted(userId),
                transactionId, currency, amount, entryType);
    }

    private void post(String path, UUID transactionId, String currency,
                      BigDecimal amount, String entryType) {
        try {
            restClient.post()
                    .uri(path)
                    .body(Map.of(
                            "transactionId", transactionId,
                            "currency", currency,
                            "amount", amount,
                            "entryType", entryType))
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpStatusCodeException e) {
            throw translate(e);

        } catch (ResourceAccessException e) {
            // Connection refused, or the read timed out: the posting may or may not have landed.
            throw new WalletUnavailableException("Wallet call failed: " + path, e);
        }
    }

    /**
     * Maps HTTP status to a saga decision. The distinction matters: 422 means nothing was applied
     * and the trade must fail, 409 means it was already applied and the caller should proceed, and
     * anything else leaves the outcome unknown.
     */
    private RuntimeException translate(HttpStatusCodeException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());

        if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
            return new InsufficientFundsException(e.getResponseBodyAsString());
        }
        if (status == HttpStatus.CONFLICT) {
            return new AlreadyAppliedException(e.getResponseBodyAsString());
        }
        return new WalletUnavailableException(
                "Wallet returned %s: %s".formatted(e.getStatusCode(), e.getResponseBodyAsString()));
    }
}
