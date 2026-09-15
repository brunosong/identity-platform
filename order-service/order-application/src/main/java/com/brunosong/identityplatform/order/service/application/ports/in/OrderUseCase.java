package com.brunosong.identityplatform.order.service.application.ports.in;

import com.brunosong.identityplatform.order.service.domain.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 주문 유스케이스.
 *
 * <p>여기에는 "누가 부를 수 있는가" 가 없다. 그 판단은 토큰을 읽을 수 있는 웹 계층이 한다.
 *
 * <p>다만 <b>"이 데이터가 이 사람 것인가" 는 여기 있다.</b> {@link #findOwned} 가 주문번호와
 * customerId 를 함께 받는 것이 그 자리다. 소유자를 인자로 받지 않는 단건 조회를 두면, 부르는 쪽이
 * 확인을 잊는 순간 남의 주문이 열린다. 아예 못 부르게 만드는 편이 낫다.
 */
public interface OrderUseCase {

    /** 이 사람의 주문 목록. 최근 것이 먼저다. */
    List<Order> findMine(String customerId);

    /**
     * 이 사람의 주문 하나. 남의 주문번호를 넣으면 <b>없는 것과 같다</b>.
     *
     * <p>소유자를 조건에 함께 걸므로 결과가 비어 있는 이유가 둘로 갈리지 않는다. 부르는 쪽은
     * 그 둘을 구분할 수 없고, 구분하지 못하는 편이 맞다. 구분해 주면 "그 주문번호는 존재한다" 는
     * 사실이 새어나간다.
     */
    Optional<Order> findOwned(String customerId, String orderId);

    Order place(String customerId, String productName, int quantity, BigDecimal unitPrice);
}
