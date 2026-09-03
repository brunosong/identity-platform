package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 이메일 계정 — 이메일(OTP)로 로그인하는 인증 수단. 한 {@link Principal} 에 매달린다(principalId 참조).
 *
 * <p>비밀번호가 없는(passwordless) 로그인 식별자다. OTP 코드 자체는 별도({@link EmailOtpChallenge})로 다루고,
 * 이 애그리거트는 "이 이메일이 어느 Principal 인가"의 매핑만 소유한다. auth 가 이메일로 주체를 독립 해석하도록
 * (customer/employee 등 다른 BC 비의존) auth 안에 둔다. 소셜(OAuth2)도 같은 방식의 별도 계정 애그리거트로 추가한다.
 *
 * <p>{@code subjectType} 을 함께 갖는다. 같은 사람이 직원이면서 포탈 고객일 수 있고 그 둘은 realm 이 다른
 * 별개 신원이라, 이메일의 유일성은 전역이 아니라 주체 유형 안에서만 성립한다. 조회할 때도 유형을 함께 준다 —
 * 이메일만으로 찾으면 상대 realm 의 주체가 걸려 엉뚱한 토큰이 나간다.
 */
@Getter
public class EmailAccount {

    private final String emailAccountId;
    private final PrincipalId principalId;
    private final SubjectType subjectType;
    private final String email;
    private final Instant createdAt;

    private EmailAccount(String emailAccountId, PrincipalId principalId, SubjectType subjectType,
                         String email, Instant createdAt) {
        this.emailAccountId = emailAccountId;
        this.principalId = principalId;
        this.subjectType = subjectType;
        this.email = email;
        this.createdAt = createdAt;
    }

    public static EmailAccount create(PrincipalId principalId, SubjectType subjectType, String email) {
        if (principalId == null) throw new IllegalArgumentException("principalId must not be null");
        if (subjectType == null) throw new IllegalArgumentException("subjectType must not be null");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email must not be blank");
        return new EmailAccount(UUID.randomUUID().toString(), principalId, subjectType, email, Instant.now());
    }

    public static EmailAccount restore(String emailAccountId, PrincipalId principalId, SubjectType subjectType,
                                       String email, Instant createdAt) {
        return new EmailAccount(emailAccountId, principalId, subjectType, email, createdAt);
    }
}
