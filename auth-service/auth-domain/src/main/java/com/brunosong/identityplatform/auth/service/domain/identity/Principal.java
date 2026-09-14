package com.brunosong.identityplatform.auth.service.domain.identity;

import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalStatus;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.Getter;

import java.time.Instant;

/**
 * 인증 신원 애그리거트.
 *
 * <p>한 주체(subjectId+realm)에 대한 인증 정체성. 자격증명(PasswordAccount/EmailAccount 등)은 별도 애그리거트로
 * principalId 로 연결된다. auth-agnostic 커널이며 customer 내부 PK 는 모른다(subjectId 로만 가리킴).
 *
 * <p>역할/권한(RBAC)은 이 애그리거트가 소유하지 않는다 — authz 도메인(authz_subject_role)이 단일 소스이며,
 * 토큰 발급 시 그쪽에서 조회해 싣는다. (과거 Principal 이 별도로 보유하던 역할 모델은 인가에 쓰이지 않아 제거했다.)
 */
@Getter
public class Principal {

    private final PrincipalId principalId;
    private final SubjectId subjectId;
    private final Realm realm;
    private PrincipalStatus status;
    private Instant lastAuthenticatedAt;

    private Principal(PrincipalId principalId, SubjectId subjectId, Realm realm,
                      PrincipalStatus status, Instant lastAuthenticatedAt) {
        this.principalId = principalId;
        this.subjectId = subjectId;
        this.realm = realm;
        this.status = status;
        this.lastAuthenticatedAt = lastAuthenticatedAt;
    }

    public static Principal create(SubjectId subjectId, Realm realm) {
        return new Principal(PrincipalId.newId(), subjectId, realm, PrincipalStatus.ACTIVE, null);
    }

    public static Principal restore(PrincipalId principalId, SubjectId subjectId, Realm realm,
                                    PrincipalStatus status, Instant lastAuthenticatedAt) {
        return new Principal(principalId, subjectId, realm, status, lastAuthenticatedAt);
    }

    public void markAuthenticated() {
        ensureActive();
        this.lastAuthenticatedAt = Instant.now();
    }

    private void ensureActive() {
        if (status != PrincipalStatus.ACTIVE) {
            throw new IllegalStateException("Principal not active: " + status);
        }
    }
}
