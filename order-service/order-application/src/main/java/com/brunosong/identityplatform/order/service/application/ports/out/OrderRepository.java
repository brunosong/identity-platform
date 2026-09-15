package com.brunosong.identityplatform.order.service.application.ports.out;

import com.brunosong.identityplatform.order.service.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * 주문 저장 드리븐 포트.
 *
 * <p>주문번호만으로 찾는 메서드를 두지 않는다. 그런 것이 있으면 언젠가 소유권 확인 없이 불린다.
 */
public interface OrderRepository {

    List<Order> findByCustomerId(String customerId);

    Optional<Order> findByCustomerIdAndOrderId(String customerId, String orderId);

    Order save(Order order);
}
