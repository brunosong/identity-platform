package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * realm 의 현재 RBAC 리비전. 토큰 발급 때 claim 에 싣고, 검증 때 이 값과 비교한다.
 */
public interface GetAuthorizationRevisionUseCase {

    long current(Realm realm);
}
