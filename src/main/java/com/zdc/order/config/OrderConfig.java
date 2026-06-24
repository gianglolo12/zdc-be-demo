package com.zdc.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires Order config properties + a shared RestClient builder for the gateways. */
@Configuration
@EnableConfigurationProperties({OrderProperties.class, DownstreamProperties.class})
public class OrderConfig {

    @Bean
    public RestClient.Builder orderRestClientBuilder(DownstreamProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) props.getReadTimeout().toMillis());
        return RestClient.builder().requestFactory(factory);
    }
}
