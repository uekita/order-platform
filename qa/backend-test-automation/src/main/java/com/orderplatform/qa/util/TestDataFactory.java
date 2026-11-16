package com.orderplatform.qa.util;

import com.orderplatform.qa.api.model.CreateOrderRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TestDataFactory {

    private static final List<String> CURRENCIES = List.of("CAD", "USD", "EUR");

    private TestDataFactory() {
    }

    public static CreateOrderRequest newOrder() {
        long cents = ThreadLocalRandom.current().nextLong(100, 500_000);
        return new CreateOrderRequest(
                UUID.randomUUID(),
                BigDecimal.valueOf(cents, 2),
                CURRENCIES.get(ThreadLocalRandom.current().nextInt(CURRENCIES.size()))
        );
    }
}
