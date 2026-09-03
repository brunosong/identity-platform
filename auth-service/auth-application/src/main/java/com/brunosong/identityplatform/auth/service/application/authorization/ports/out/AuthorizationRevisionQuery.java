package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * realm 별 RBAC 리비전 조회 포트(SPI). 토큰 발급 때 claim 에 실을 값을 읽는다.
 */
public interface AuthorizationRevisionQuery {

    long current(Realm realm);
}
