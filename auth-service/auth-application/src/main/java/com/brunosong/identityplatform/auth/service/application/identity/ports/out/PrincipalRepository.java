package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

import java.util.Optional;

/**
 * Principal 저장 드리븐 포트.
 *
 * <p>주체는 {@code (subjectType, subjectId)} 로 유일하다. subjectId 만으로 찾으면 유일키의 절반으로
 * 찾는 셈이라 상대 realm 의 주체가 걸릴 수 있다 — 두 realm 의 식별자는 서로 다른 체계에서 발급된다.
 */
public interface PrincipalRepository {

    Optional<Principal> findById(PrincipalId principalId);

    Optional<Principal> findBySubjectId(SubjectType subjectType, SubjectId subjectId);

    Principal save(Principal principal);
}
