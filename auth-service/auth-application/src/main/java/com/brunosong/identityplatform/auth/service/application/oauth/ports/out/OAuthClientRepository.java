package com.brunosong.identityplatform.auth.service.application.oauth.ports.out;

import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 등록된 클라이언트 조회 드리븐 포트.
 *
 * <p>조회에 realm 을 함께 받는다. client_id 는 전역에서 유일하지만, 인가 요청은 경로의 realm 으로
 * 들어오므로 그 realm 의 앱이 맞는지까지 같은 조회에서 가린다. 고객 앱의 client_id 로 어드민
 * 로그인을 시작하는 길을 막는 자리다.
 *
 * <p>모르는 앱이든 realm 이 어긋난 앱이든 결과는 빈 값으로 같다. 호출자가 둘을 구분할 이유가 없다.
 * 어느 쪽이든 리다이렉트하지 않고 우리 화면에서 끝내야 한다.
 */
public interface OAuthClientRepository {

    Optional<OAuthClient> findByClientId(Realm realm, String clientId);
}
