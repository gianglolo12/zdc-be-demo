package com.zdc.order.gateway;

import java.time.Instant;

/** Port to Payment Service init-and-submit against ESP Payment VN (FS-004). */
public interface PaymentGateway {

    /**
     * Synchronously initialise a transaction and submit it to the PSP.
     * Throws {@link PaymentGatewayException} when the gateway rejects/times out,
     * which the caller compensates by releasing the Commercial credit.
     */
    PaymentInitResult initAndSubmit(PaymentInitRequest request);

    record PaymentInitRequest(
            Long agentId,
            String orderLocalId,
            Long channelId,
            String paymentMethod,
            String paymentProvider,
            long amount,
            String currency,
            String returnUrl) {
    }

    record PaymentInitResult(
            Long paymentTransId,
            String pspTransId,
            String paymentUrl,
            String qrCode,
            String deeplink,
            Instant expiredAt) {
    }

    /** Raised when the PSP submit fails after retries. */
    class PaymentGatewayException extends RuntimeException {
        public PaymentGatewayException(String message) {
            super(message);
        }

        public PaymentGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
