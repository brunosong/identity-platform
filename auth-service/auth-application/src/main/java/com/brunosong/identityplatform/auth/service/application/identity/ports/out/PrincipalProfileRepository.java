package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.PrincipalProfile;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;

import java.util.Optional;

/**
 * 신원 표시 속성 저장 드리븐 포트.
 *
 * <p>조회에 realm 을 받지 않는다. {@code principalId} 가 이미 realm 을 포함한 신원을 가리키므로
 * 그 값 하나면 유일하다 — 자격증명 포트들이 {@code (realm, 식별자)} 를 받는 것과 다른 이유다.
 * 저쪽은 사람이 입력한 값(이메일·아이디)으로 찾고, 여기는 이미 확정된 신원으로 찾는다.
 */
public interface PrincipalProfileRepository {

    Optional<PrincipalProfile> findByPrincipalId(PrincipalId principalId);

    PrincipalProfile save(PrincipalProfile profile);
}
