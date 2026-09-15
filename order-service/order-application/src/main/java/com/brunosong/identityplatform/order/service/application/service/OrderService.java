package com.brunosong.identityplatform.order.service.application.service;

import com.brunosong.identityplatform.order.service.application.ports.in.OrderUseCase;
import com.brunosong.identityplatform.order.service.application.ports.out.OrderRepository;
import com.brunosong.identityplatform.order.service.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 주문 응용 서비스.
 *
 * <p>토큰도 realm 도 모른다. 받은 customerId 로 일할 뿐이고, 그 값이 믿을 만한지는 부르는 쪽이 이미
 * 확인했다. 그래서 인증 방식이 바뀌어도 이 계층은 손대지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderService implements OrderUseCase {

    private final OrderRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<Order> findMine(String customerId) {
        return repository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findOwned(String customerId, String orderId) {
        return repository.findByCustomerIdAndOrderId(customerId, orderId);
    }

    @Override
    @Transactional
    public Order place(String customerId, String productName, int quantity, BigDecimal unitPrice) {
        return repository.save(Order.place(customerId, productName, quantity, unitPrice));
    }
}
