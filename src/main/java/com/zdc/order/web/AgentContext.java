package com.zdc.order.web;

/** Agent identity injected by the Gateway from the JWT (G2.F06). */
public record AgentContext(Long agentId, String status, String country) {

    public static final String ACTIVE = "ACTIVE";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
