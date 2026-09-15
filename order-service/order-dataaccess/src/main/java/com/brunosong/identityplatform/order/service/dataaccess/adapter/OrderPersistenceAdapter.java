package com.brunosong.identityplatform.order.service.dataaccess.adapter;

import com.brunosong.identityplatform.order.service.application.ports.out.OrderRepository;
import com.brunosong.identityplatform.order.service.dataaccess.entity.OrderJpaEntity;
import com.brunosong.identityplatform.order.service.dataaccess.repository.OrderJpaRepository;
import com.brunosong.identityplatform.order.service.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** {@link OrderRepository} 영속성 어댑터. */
@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderRepository {

    private final OrderJpaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByCustomerId(String customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByCustomerIdAndOrderId(String customerId, String orderId) {
        return repository.findByCustomerIdAndOrderId(customerId, orderId)
                .map(OrderPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Order save(Order order) {
        return toDomain(repository.save(toEntity(order)));
    }

    private static OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity e = new OrderJpaEntity();
        e.setOrderId(order.getOrderId());
        e.setCustomerId(order.getCustomerId());
        e.setProductName(order.getProductName());
        e.setQuantity(order.getQuantity());
        e.setAmount(order.getAmount());
        e.setStatus(order.getStatus());
        e.setCreatedAt(order.getCreatedAt());
        return e;
    }

    private static Order toDomain(OrderJpaEntity e) {
        return Order.restore(e.getOrderId(), e.getCustomerId(), e.getProductName(), e.getQuantity(),
                e.getAmount(), e.getStatus(), e.getCreatedAt());
    }
}
