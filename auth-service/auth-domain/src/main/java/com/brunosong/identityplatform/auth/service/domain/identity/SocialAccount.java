package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 소셜 자격증명. (provider, providerUid) 로 외부 신원을 가리키고 principalId 로 Principal 과 연결한다.
 * {@link PasswordAccount}/{@link EmailAccount} 와 나란한 수단별 계정 애그리거트다.
 *
 * <p>비밀(토큰/시크릿)을 저장하지 않는다 — 자격 검증은 provider 가 하고, auth 는 "이 외부 신원이 이
 * Principal 이다"라는 연결만 보관한다.
 *
 * <p>연결은 {@code (subjectType, provider, providerUid)} 로 유일하다 — 다른 수단과 같은 규칙이다.
 * providerUid 는 provider 안에서 전역 유일이라 아이디 충돌은 없지만, 한 사람이 직원이면서 고객일 때
 * 같은 소셜 계정이 두 신원에 각각 연결될 수 있어야 한다. 무엇보다 유형 없이 조회하면 상대 realm 의
 * 연결이 걸려, 소셜 로그인 한 번으로 다른 realm 토큰이 나간다.
 */
@Getter
public class SocialAccount {

    private final String socialAccountId;
    private final PrincipalId principalId;
    private final SubjectType subjectType;
    private final SocialProvider provider;
    private final String providerUid;
    private final Instant createdAt;

    private SocialAccount(String socialAccountId, PrincipalId principalId, SubjectType subjectType,
                          SocialProvider provider, String providerUid, Instant createdAt) {
        this.socialAccountId = socialAccountId;
        this.principalId = principalId;
        this.subjectType = subjectType;
        this.provider = provider;
        this.providerUid = providerUid;
        this.createdAt = createdAt;
    }

    public static SocialAccount create(PrincipalId principalId, SubjectType subjectType,
                                       SocialProvider provider, String providerUid) {
        if (principalId == null) throw new IllegalArgumentException("principalId must not be null");
        if (subjectType == null) throw new IllegalArgumentException("subjectType must not be null");
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        if (providerUid == null || providerUid.isBlank()) {
            throw new IllegalArgumentException("providerUid must not be blank");
        }
        return new SocialAccount(UUID.randomUUID().toString(), principalId, subjectType, provider,
                providerUid, Instant.now());
    }

    public static SocialAccount restore(String socialAccountId, PrincipalId principalId,
                                        SubjectType subjectType, SocialProvider provider,
                                        String providerUid, Instant createdAt) {
        return new SocialAccount(socialAccountId, principalId, subjectType, provider, providerUid, createdAt);
    }
}
