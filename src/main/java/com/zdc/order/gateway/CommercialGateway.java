package com.zdc.order.gateway;

/** Port to Commercial Service credit management (FS-003 read, FS-004 charge/release). */
public interface CommercialGateway {

    /** Current credit snapshot for an agent. */
    CreditInfo getAvailableCredit(Long agentId);

    /**
     * Atomic charge (SELECT FOR UPDATE + decrement + history INSERT) at create time.
     * Returns {@code charged=false} with the current available credit when the
     * balance is insufficient — the caller maps it to the right error code.
     */
    ChargeResult charge(Long agentId, String orderLocalId, long amount);

    /** Compensating release of a previous charge (rollback when downstream fails). */
    void release(Long agentId, String orderLocalId);

    record CreditInfo(Long id, Long agentId, Long creditLimit, Long availableCredit) {
    }

    record ChargeResult(boolean charged, Long availableCredit) {
    }
}
