package com.tradingplatform.trade.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    List<Trade> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Limit limit);

    /** Sagas abandoned mid-flight, for the recovery scan. Backed by idx_trades_unresolved. */
    List<Trade> findByStatusIn(List<TradeStatus> statuses);
}
