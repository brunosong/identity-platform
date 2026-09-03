package com.brunosong.identityplatform.auth.service.domain.identity.valueobject;

import java.util.UUID;

/**
 * 주체 식별자 — auth 가 채번하고 대상 BC(customer/employee)가 자기 식별자로 받는다.
 * EMPLOYEE=esntlId, CUSTOMER=customerUuid.
 * auth 는 이 값으로만 주체를 가리키고 내부 PK(customer_seq 등)는 모른다.
 */
public record SubjectId(String value) {

    public SubjectId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
    }

    /** 새 주체 식별자를 채번한다. 대상 BC 가 만들어 주기를 기다리지 않는다. */
    public static SubjectId generate() {
        return new SubjectId(UUID.randomUUID().toString());
    }
}
