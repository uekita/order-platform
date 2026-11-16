package com.orderplatform.order.service;

import com.orderplatform.order.domain.Order;
import com.orderplatform.order.event.OrderEventPublisher;
import com.orderplatform.order.repository.OrderRepository;
import com.orderplatform.order.web.CreateOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(CreateOrderRequest request, String correlationId) {
        Order order = new Order(
                request.customerId(),
                request.amount(),
                request.currency().toUpperCase(Locale.ROOT),
                correlationId
        );
        Order saved = orderRepository.saveAndFlush(order);
        eventPublisher.publishOrderCreated(saved, correlationId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Order get(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
