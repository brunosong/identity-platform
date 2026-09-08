package com.brunosong.identityplatform.customer.service.dataaccess.repository;

import com.brunosong.identityplatform.customer.service.dataaccess.entity.CustomerProfileJpaEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfileJpaEntity, String> {

    /**
     * 이름/이메일 부분 일치. 소문자로 맞춰 비교한다 — 대소문자가 다르다고 못 찾으면 검색이 아니다.
     * 빈 키워드는 전체 목록으로 다룬다(상한은 호출부가 건다).
     */
    @Query("""
            SELECT p FROM CustomerProfileJpaEntity p
            WHERE :keyword IS NULL
               OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY p.createdAt DESC
            """)
    List<CustomerProfileJpaEntity> search(String keyword, Limit limit);
}
