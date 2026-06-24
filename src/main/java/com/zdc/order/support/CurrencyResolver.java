package com.zdc.order.support;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves the order currency from an agent country code (snapshot at create —
 * FRS "currency snapshot từ countries.currency"). P1 only ships VN; the map is
 * the single place to extend when SEA expansion (P5) lands.
 */
@Component
public class CurrencyResolver {

    private static final Map<String, String> CURRENCY_BY_COUNTRY = Map.of("vn", "VND");
    private static final String DEFAULT_CURRENCY = "VND";

    public String resolve(String countryCode) {
        if (countryCode == null) {
            return DEFAULT_CURRENCY;
        }
        return CURRENCY_BY_COUNTRY.getOrDefault(countryCode.toLowerCase(Locale.ROOT), DEFAULT_CURRENCY);
    }
}
