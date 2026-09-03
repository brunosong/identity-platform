package com.brunosong.identityplatform.auth.service.application.authorization.ports.in;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

/**
 * URL 리소스를 지운다. 지운 즉시 인가 캐시를 갱신한다.
 */
public interface DeleteUrlAccessUseCase {

    void delete(Realm realm, Long urlAccessId);
}
