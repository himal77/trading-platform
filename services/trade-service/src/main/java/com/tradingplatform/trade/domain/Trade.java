package com.tradingplatform.trade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Side side;

    @Column(nullable = false, updatable = false)
    private String asset;

    @Column(name = "quote_currency", nullable = false, updatable = false)
    private String quoteCurrency;

    @Column(nullable = false, updatable = false)
    private BigDecimal quantity;

    /** Price at execution, persisted because a historical trade must show what was actually charged. */
    @Column(nullable = false, updatable = false)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trade() {
    }

    public static Trade initiate(UUID userId, Side side, String asset,
                                 String quoteCurrency, BigDecimal quantity, BigDecimal price) {
        Trade trade = new Trade();
        trade.id = UUID.randomUUID();
        trade.userId = userId;
        trade.side = side;
        trade.asset = asset;
        trade.quoteCurrency = quoteCurrency;
        trade.quantity = quantity;
        trade.price = price;
        // Rounded to the quote currency's precision; an unrounded product would not match the
        // amount actually debited.
        trade.total = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
        trade.status = TradeStatus.PENDING;
        trade.createdAt = Instant.now();
        trade.updatedAt = trade.createdAt;
        return trade;
    }

    public void markDebited() {
        transitionTo(TradeStatus.DEBITED);
    }

    public void markCompleted() {
        transitionTo(TradeStatus.COMPLETED);
    }

    public void markCompensating(String reason) {
        this.failureReason = reason;
        transitionTo(TradeStatus.COMPENSATING);
    }

    public void markFailed(String reason) {
        this.failureReason = reason;
        transitionTo(TradeStatus.FAILED);
    }

    /**
     * Funds were taken and the refund also failed. No automated path remains, so the row is left
     * in a state that operational alerting polls for.
     */
    public void markCompensationFailed(String reason) {
        this.failureReason = reason;
        transitionTo(TradeStatus.COMPENSATION_FAILED);
    }

    private void transitionTo(TradeStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTradeTransitionException(id, status, target);
        }
        this.status = target;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Side getSide() {
        return side;
    }

    public String getAsset() {
        return asset;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public TradeStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Side {
        BUY, SELL
    }
}
