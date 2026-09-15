package com.brunosong.identityplatform.order.service.dataaccess.repository;

import com.brunosong.identityplatform.order.service.dataaccess.entity.OrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 조회는 언제나 customerId 로 좁힌다.
 *
 * <p>{@code findById} 가 {@link JpaRepository} 에 이미 있지만 이 서비스는 그것을 부르지 않는다.
 * 부를 자리마다 소유권 확인을 따로 적어야 하고, 한 곳에서 잊으면 남의 주문이 열리기 때문이다.
 * 아래 두 메서드는 소유자를 조건에 박아 두어 잊을 자리 자체가 없다.
 */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {

    List<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<OrderJpaEntity> findByCustomerIdAndOrderId(String customerId, String orderId);
}
