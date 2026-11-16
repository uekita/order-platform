package com.orderplatform.order.web;

import com.orderplatform.order.domain.Order;
import com.orderplatform.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationHeader) {
        String correlationId = CorrelationId.from(correlationHeader);
        Order order = orderService.create(request, correlationId);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + order.getId()))
                .header(CorrelationId.HEADER, correlationId)
                .body(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> get(
            @PathVariable UUID orderId,
            @RequestHeader(value = CorrelationId.HEADER, required = false) String correlationHeader) {
        String correlationId = CorrelationId.from(correlationHeader);
        Order order = orderService.get(orderId);
        return ResponseEntity
                .ok()
                .header(CorrelationId.HEADER, correlationId)
                .body(OrderResponse.from(order));
    }
}
