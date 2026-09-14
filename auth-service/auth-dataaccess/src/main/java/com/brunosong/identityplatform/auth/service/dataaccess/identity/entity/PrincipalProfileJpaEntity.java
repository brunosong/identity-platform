package com.brunosong.identityplatform.auth.service.dataaccess.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * identity_principal_profile — 신원의 표시 속성. PK 가 principal_id 다(1:1).
 *
 * <p>{@code identity_principal} 에 열로 붙이지 않는다. 그 테이블은 인증 커널이고, 표시용 값이
 * 섞이면 "이름이 바뀌었다" 는 이유로 인증 애그리거트를 열게 된다.
 */
@Entity
@Table(name = "identity_principal_profile")
@Getter
@Setter
@NoArgsConstructor
public class PrincipalProfileJpaEntity {

    @Id
    @Column(name = "principal_id", length = 36)
    private String principalId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 표시용이다. 로그인 수단으로서의 번호(SMS 인증)는 자격증명이라 별도 애그리거트가 된다. */
    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
