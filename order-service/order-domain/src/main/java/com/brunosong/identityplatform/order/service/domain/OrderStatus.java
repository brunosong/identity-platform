package com.brunosong.identityplatform.order.service.domain;

/**
 * 주문 상태.
 *
 * <p>아직 {@code PLACED} 하나뿐이다. 이 저장소의 주제가 인증과 인가라 결제나 배송 같은 상태 전이는
 * 다루지 않는다. 그래도 칸을 둔 것은 이후 상태를 다룰 때 표를 고치지 않으려는 것이고, 지금은
 * 여기 값이 하나라는 사실이 곧 "이 서비스에는 주문 취소 API 가 없다" 는 뜻이다.
 */
public enum OrderStatus {
    PLACED
}
