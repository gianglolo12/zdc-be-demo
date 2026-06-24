package com.zdc.order.web;

import com.zdc.order.error.ErrorCode;
import com.zdc.order.error.OrderException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves + validates the X-Agent-* headers (FS-001..004 step 1-2). Missing/blank
 * headers -> ERR_INVALID_REQUEST (400); non-ACTIVE status -> ERR_AGENT_NOT_ACTIVE (403).
 */
@Component
public class AgentHeaderResolver {

    public AgentContext resolve(String agentId, String status, String country) {
        if (!StringUtils.hasText(agentId) || !StringUtils.hasText(status) || !StringUtils.hasText(country)) {
            throw new OrderException(ErrorCode.ERR_INVALID_REQUEST);
        }
        long id;
        try {
            id = Long.parseLong(agentId.trim());
        } catch (NumberFormatException ex) {
            throw new OrderException(ErrorCode.ERR_INVALID_REQUEST);
        }
        AgentContext ctx = new AgentContext(id, status.trim(), country.trim());
        if (!ctx.isActive()) {
            throw new OrderException(ErrorCode.ERR_AGENT_NOT_ACTIVE);
        }
        return ctx;
    }
}
