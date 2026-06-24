package com.zdc.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Base URLs + timeouts for the internal services Order calls (BO/Pricing/Commercial/Payment). */
@ConfigurationProperties(prefix = "zdc.downstream")
public class DownstreamProperties {

    private String backOfficeUrl = "http://localhost:8081";
    private String pricingUrl = "http://localhost:8082";
    private String commercialUrl = "http://localhost:8083";
    private String paymentUrl = "http://localhost:8084";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getBackOfficeUrl() {
        return backOfficeUrl;
    }

    public void setBackOfficeUrl(String backOfficeUrl) {
        this.backOfficeUrl = backOfficeUrl;
    }

    public String getPricingUrl() {
        return pricingUrl;
    }

    public void setPricingUrl(String pricingUrl) {
        this.pricingUrl = pricingUrl;
    }

    public String getCommercialUrl() {
        return commercialUrl;
    }

    public void setCommercialUrl(String commercialUrl) {
        this.commercialUrl = commercialUrl;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
