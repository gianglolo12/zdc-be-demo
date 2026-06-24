package com.zdc.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled outbox polling outside the test profile. */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
