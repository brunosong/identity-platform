package com.brunosong.identityplatform.customer.service.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * customer_profile — 고객 프로필.
 *
 * <p>PK 가 auth 의 subjectId 다. 이 서비스가 따로 채번하지 않는다. 다만 <b>외래키는 걸 수 없다</b> —
 * auth 의 테이블은 다른 서비스, 다른 데이터베이스에 있다. 서비스를 나눈다는 것은 참조 무결성을
 * DB 에 맡기지 못하게 된다는 뜻이기도 하다.
 */
@Entity
@Table(name = "customer_profile")
@Getter
@Setter
@NoArgsConstructor
public class CustomerProfileJpaEntity {

    @Id
    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
