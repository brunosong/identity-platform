package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * URL 접근 규칙 캐시를 저장소에서 다시 읽는다.
 */
public interface ReloadAccessRulesUseCase {

    void reload(Realm realm);
}
