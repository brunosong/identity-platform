package com.brunosong.identityplatform.auth.service.application.authorization.ports.out;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * realm 별 RBAC 리비전 쓰기 포트(SPI). 권한/역할 매핑이 바뀌면 올린다.
 */
public interface AuthorizationRevisionRepository {

    void bump(Realm realm);
}
