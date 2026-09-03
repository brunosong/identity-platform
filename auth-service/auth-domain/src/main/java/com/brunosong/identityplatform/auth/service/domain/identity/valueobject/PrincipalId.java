package com.brunosong.identityplatform.auth.service.domain.identity.valueobject;

import java.util.UUID;

/**
 * Principal(인증 신원) 식별자. UUID 문자열.
 */
public record PrincipalId(String value) {

    public PrincipalId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
    }

    public static PrincipalId newId() {
        return new PrincipalId(UUID.randomUUID().toString());
    }
}
