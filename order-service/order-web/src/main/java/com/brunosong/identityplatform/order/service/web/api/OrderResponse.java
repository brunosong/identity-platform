package com.brunosong.identityplatform.order.service.web.api;

import com.brunosong.identityplatform.order.service.domain.Order;
import com.brunosong.identityplatform.order.service.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 주문 응답.
 *
 * <p>도메인 객체를 그대로 내보내지 않는다. 도메인이 바뀔 때마다 API 계약이 함께 흔들리면
 * 부르는 쪽이 이유 없이 깨진다.
 *
 * <p>{@code customerId} 를 싣지 않는다. 자기 주문만 볼 수 있으므로 받는 쪽이 이미 아는 값이고,
 * 응답에 남의 식별자가 실릴 여지를 아예 없앤다.
 */
public record OrderResponse(
        String orderId,
        String productName,
        int quantity,
        BigDecimal amount,
        OrderStatus status,
        Instant createdAt
) {

    public static OrderResponse of(Order order) {
        return new OrderResponse(order.getOrderId(), order.getProductName(), order.getQuantity(),
                order.getAmount(), order.getStatus(), order.getCreatedAt());
    }
}
