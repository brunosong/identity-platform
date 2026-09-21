package com.brunosong.identityplatform.auth.service.application.oauth.ports.out;

import com.brunosong.identityplatform.auth.service.domain.oauth.OAuthClient;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
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

    /**
     * 등록된 앱 전부. 관리 화면이 쓴다.
     *
     * <p>realm 으로 좁히지 않는다. 이 목록을 보는 사람은 두 realm 을 다 운영하는 사람이고,
     * 어느 앱이 어느 realm 에 붙어 있는지가 목록에서 바로 보여야 한다.
     */
    List<OAuthClient> findAll();

    /**
     * 이 client_id 가 이미 쓰이고 있는가. realm 을 받지 않는다 - client_id 는 전역에서 유일하다.
     *
     * <p>조회가 realm 을 받는 것과 다른 이유다. 저쪽은 "이 realm 의 앱이 맞는가" 를 묻고,
     * 이쪽은 "이 이름이 비어 있는가" 를 묻는다. 다른 realm 이 이미 쓰고 있어도 비어 있지 않다.
     */
    boolean exists(String clientId);

    OAuthClient save(OAuthClient client);
}
