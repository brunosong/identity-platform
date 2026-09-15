package com.brunosong.identityplatform.order.service.dataaccess.entity;

import com.brunosong.identityplatform.order.service.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * orders — 주문.
 *
 * <p>표 이름이 {@code order} 가 아니라 {@code orders} 인 것은 {@code ORDER} 가 SQL 예약어라서다.
 * 따옴표로 감싸면 쓸 수는 있지만, 그러면 손으로 질의할 때마다 따옴표를 붙여야 한다.
 *
 * <p>{@code customer_id} 는 auth 의 {@code identity_principal} 을 가리키지만 <b>외래키는 걸 수 없다</b>.
 * 다른 서비스, 다른 데이터베이스다. customer-service 의 {@code customer_profile} 과도 마찬가지고,
 * 프로필 없이 주문만 있는 상태도 정상이다 — 두 서비스는 서로를 기다리지 않는다.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderJpaEntity {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // 이름으로 저장한다. 순서로 저장하면 상태를 하나 끼워 넣는 날 기존 행의 뜻이 통째로 바뀐다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
