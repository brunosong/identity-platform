package com.brunosong.identityplatform.order.service.application.service;

import com.brunosong.identityplatform.order.service.application.ports.in.OrderUseCase;
import com.brunosong.identityplatform.order.service.application.ports.out.OrderRepository;
import com.brunosong.identityplatform.order.service.application.ports.out.ProductRepository;
import com.brunosong.identityplatform.order.service.domain.Order;
import com.brunosong.identityplatform.order.service.domain.OutOfStockException;
import com.brunosong.identityplatform.order.service.domain.Product;
import com.brunosong.identityplatform.order.service.domain.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProductRepository products;

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
    public Order place(String customerId, String productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        // 수량 검증이 여기서 끝난다. 재고를 줄이기 전이어야 한다. 음수 수량이 닿으면 재고가 늘어난다.
        Order order = Order.place(customerId, product, quantity);

        // 재고를 줄이는 것과 주문을 남기는 것이 한 트랜잭션이다. 주문 저장이 실패하면 재고도 돌아온다.
        boolean taken = products.takeStock(productId, quantity);
        if (!taken) {
            throw new OutOfStockException(product.getName());
        }
        return repository.save(order);
    }
}
