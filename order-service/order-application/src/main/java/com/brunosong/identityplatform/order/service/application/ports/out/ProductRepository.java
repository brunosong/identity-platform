package com.brunosong.identityplatform.order.service.application.ports.out;

import com.brunosong.identityplatform.order.service.domain.Product;

import java.util.List;
import java.util.Optional;

/** 상품 저장 드리븐 포트. */
public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(String productId);

    /**
     * 남은 수량이 충분할 때만 줄인다. 줄였으면 true, 모자라서 그대로 두었으면 false.
     *
     * <p>읽고 비교하고 저장하는 세 걸음으로 나누면, 마지막 하나를 두 사람이 동시에 사는 순간 둘 다
     * 통과한다. 조건을 건 갱신 한 번이면 데이터베이스가 둘 중 하나만 통과시킨다.
     */
    boolean takeStock(String productId, int quantity);
}
