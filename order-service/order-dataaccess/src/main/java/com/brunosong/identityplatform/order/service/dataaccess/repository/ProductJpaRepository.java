package com.brunosong.identityplatform.order.service.dataaccess.repository;

import com.brunosong.identityplatform.order.service.dataaccess.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, String> {

    List<ProductJpaEntity> findAllByOrderByProductIdAsc();

    /**
     * 남은 수량이 충분할 때만 줄인다. 줄인 행 수를 돌려준다(1 이면 줄였고 0 이면 모자랐다).
     *
     * <p>비교와 차감이 한 문장이라 동시에 들어온 두 주문이 같은 마지막 하나를 함께 가져가지 못한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update ProductJpaEntity p
               set p.stock = p.stock - :quantity
             where p.productId = :productId
               and p.stock >= :quantity
            """)
    int takeStock(@Param("productId") String productId, @Param("quantity") int quantity);
}
