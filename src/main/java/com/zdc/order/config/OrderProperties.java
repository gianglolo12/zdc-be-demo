package com.zdc.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Order Service tunables (lock TTL, payment return URL). */
@ConfigurationProperties(prefix = "zdc.order")
public class OrderProperties {

    /** Anti double-click lock TTL (BR-006). */
    private Duration createLockTtl = Duration.ofSeconds(10);

    /** Return URL handed to the PSP for redirect-back after payment. */
    private String paymentReturnUrl = "https://zdc.vn/api/v1/order/callback";

    public Duration getCreateLockTtl() {
        return createLockTtl;
    }

    public void setCreateLockTtl(Duration createLockTtl) {
        this.createLockTtl = createLockTtl;
    }

    public String getPaymentReturnUrl() {
        return paymentReturnUrl;
    }

    public void setPaymentReturnUrl(String paymentReturnUrl) {
        this.paymentReturnUrl = paymentReturnUrl;
    }
}
