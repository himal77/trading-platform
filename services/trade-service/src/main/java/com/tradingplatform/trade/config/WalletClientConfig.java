package com.tradingplatform.trade.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
class WalletClientConfig {

    /**
     * The read timeout is the innermost layer of the resilience stack. Without it a hung wallet
     * would hold this service's threads until the connection eventually dropped, and the circuit
     * breaker would never see a failure to trip on.
     *
     * <p>It must also be shorter than any caller-facing timeout, so an inner failure surfaces
     * before the outer request gives up.
     */
    @Bean
    RestClient walletRestClient(@Value("${wallet.base-url}") String baseUrl,
                                @Value("${wallet.connect-timeout:2s}") Duration connectTimeout,
                                @Value("${wallet.read-timeout:3s}") Duration readTimeout) {

        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
