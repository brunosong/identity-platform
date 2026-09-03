package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * realm 의 RBAC 리비전을 올린다. 그 realm 의 기존 토큰이 모두 무효가 되어 전원 재로그인한다.
 * 자동으로 불리지 않는다 — 운영자가 명시적으로 실행한다.
 */
public interface BumpAuthorizationRevisionUseCase {

    void bump(Realm realm);
}
