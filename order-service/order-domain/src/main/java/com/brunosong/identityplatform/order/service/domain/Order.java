package com.brunosong.identityplatform.order.service.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 주문.
 *
 * <p><b>이 서비스도 자격증명을 갖지 않는다.</b> 누가 주문했는지는 토큰의 {@code sub} 가 말해주고,
 * 그 토큰이 진짜인지는 auth 의 서명이 보장한다. 여기에는 로그인시키는 코드가 없다.
 *
 * <p>{@code customerId} 는 auth 가 채번한 subjectId 다. customer-service 의 {@code customerId} 와
 * 같은 값이지만 <b>두 서비스는 서로의 표를 보지 않는다</b>. 같은 사람을 가리키는 이유는 토큰이
 * 하나이기 때문이지, 어느 한쪽이 상대의 데이터를 읽기 때문이 아니다.
 *
 * <p>{@code orderId} 는 <b>이 서비스가 채번한다.</b> 프로필과 달리 주문은 사람당 여럿이라
 * 주체 식별자를 그대로 열쇠로 쓸 수 없다. 그 대신 이 값이 <b>요청으로 들어온다</b>.
 * {@code GET /api/orders/{orderId}} 에 남의 주문번호를 적어 넣을 수 있다는 뜻이고,
 * 그래서 조회할 때 {@code customerId} 를 조건에 함께 건다.
 */
@Getter
public class Order {

    private final String orderId;
    private final String customerId;
    private final String productName;
    private final int quantity;
    private final BigDecimal amount;
    private final OrderStatus status;
    private final Instant createdAt;

    private Order(String orderId, String customerId, String productName, int quantity,
                  BigDecimal amount, OrderStatus status, Instant createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productName = productName;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Order place(String customerId, String productName, int quantity, BigDecimal unitPrice) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        return new Order(UUID.randomUUID().toString(), customerId, productName.trim(), quantity,
                unitPrice.multiply(BigDecimal.valueOf(quantity)), OrderStatus.PLACED, Instant.now());
    }

    public static Order restore(String orderId, String customerId, String productName, int quantity,
                                BigDecimal amount, OrderStatus status, Instant createdAt) {
        return new Order(orderId, customerId, productName, quantity, amount, status, createdAt);
    }
}
