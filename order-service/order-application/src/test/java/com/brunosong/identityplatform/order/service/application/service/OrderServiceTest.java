package com.brunosong.identityplatform.order.service.application.service;

import com.brunosong.identityplatform.order.service.application.ports.out.OrderRepository;
import com.brunosong.identityplatform.order.service.application.ports.out.ProductRepository;
import com.brunosong.identityplatform.order.service.domain.Order;
import com.brunosong.identityplatform.order.service.domain.OutOfStockException;
import com.brunosong.identityplatform.order.service.domain.Product;
import com.brunosong.identityplatform.order.service.domain.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    private FakeProducts products;
    private FakeOrders orders;
    private OrderService service;

    @BeforeEach
    void setUp() {
        products = new FakeProducts();
        products.seed("kb", "키보드", 259000, 2);
        orders = new FakeOrders();
        service = new OrderService(orders, products);
    }

    @Test
    @DisplayName("가격은 상품에서 읽고 수량만큼 재고를 줄인다")
    void placeUsesServerPrice() {
        Order order = service.place("customer-1", "kb", 2);

        assertThat(order.getAmount()).isEqualByComparingTo("518000");
        assertThat(order.getProductId()).isEqualTo("kb");
        assertThat(order.getProductName()).isEqualTo("키보드");
        assertThat(products.stockOf("kb")).isZero();
        assertThat(orders.saved).hasSize(1);
    }

    @Test
    @DisplayName("재고보다 많이 주문하면 거절하고 주문을 남기지 않는다")
    void outOfStockRejected() {
        assertThatThrownBy(() -> service.place("customer-1", "kb", 3))
                .isInstanceOf(OutOfStockException.class);

        assertThat(products.stockOf("kb")).isEqualTo(2);
        assertThat(orders.saved).isEmpty();
    }

    @Test
    @DisplayName("없는 상품은 거절한다")
    void unknownProductRejected() {
        assertThatThrownBy(() -> service.place("customer-1", "nope", 1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("음수 수량은 재고에 닿기 전에 거절한다")
    void negativeQuantityNeverTouchesStock() {
        assertThatThrownBy(() -> service.place("customer-1", "kb", -5))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(products.stockOf("kb")).isEqualTo(2);
    }

    private static final class FakeProducts implements ProductRepository {

        private final Map<String, Product> byId = new HashMap<>();

        void seed(String productId, String name, long price, int stock) {
            byId.put(productId, Product.restore(productId, name, BigDecimal.valueOf(price), stock));
        }

        int stockOf(String productId) {
            return byId.get(productId).getStock();
        }

        @Override
        public List<Product> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<Product> findById(String productId) {
            return Optional.ofNullable(byId.get(productId));
        }

        @Override
        public boolean takeStock(String productId, int quantity) {
            Product product = byId.get(productId);
            if (product.getStock() < quantity) {
                return false;
            }
            byId.put(productId, Product.restore(productId, product.getName(), product.getPrice(),
                    product.getStock() - quantity));
            return true;
        }
    }

    private static final class FakeOrders implements OrderRepository {

        private final List<Order> saved = new ArrayList<>();

        @Override
        public List<Order> findByCustomerId(String customerId) {
            return saved;
        }

        @Override
        public Optional<Order> findByCustomerIdAndOrderId(String customerId, String orderId) {
            return Optional.empty();
        }

        @Override
        public Order save(Order order) {
            saved.add(order);
            return order;
        }
    }
}
