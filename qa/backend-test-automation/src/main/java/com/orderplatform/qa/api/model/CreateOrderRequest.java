package com.orderplatform.qa.api.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(UUID customerId, BigDecimal amount, String currency) {
}
