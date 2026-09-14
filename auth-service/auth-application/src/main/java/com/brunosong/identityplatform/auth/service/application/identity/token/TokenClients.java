package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.List;
import java.util.Map;

/**
 * 토큰을 받아 갈 클라이언트들 — 어느 앱이 요청했고, 그 토큰을 어느 서비스가 받아들이는가.
 *
 * <h2>왜 클라이언트가 필요한가</h2>
 * 전에는 토큰에 {@code aud} 가 없었다. 그러면 <b>발급자만 맞으면 누구든 받아들인다</b> — 포털 토큰
 * 하나가 포털의 모든 서비스에 통한다. 통합 로그인은 공짜로 되지만, 서비스 하나가 침해되면 그 서비스로
 * 들어온 모든 사용자 토큰을 다른 서비스에 그대로 재생할 수 있다. 서비스가 늘수록 <b>가장 약한 서비스
 * 하나가 realm 전체의 보안 수준</b>이 된다.
 *
 * <h2>그래도 통합 로그인은 깨지지 않는다</h2>
 * 한 클라이언트가 audience 를 <b>여럿</b> 가질 수 있기 때문이다. 고객 포털이
 * {@code [customer-service, order-service]} 를 가지면 사용자는 한 번 로그인해 그 토큰 하나로 둘 다
 * 쓴다. 달라지는 것은 <b>나열되지 않은 서비스가 그 토큰을 거부한다</b>는 것뿐이다.
 *
 * <p>Keycloak 도 같은 모양이다 — SSO 세션은 하나이고, 토큰의 audience 는 클라이언트 설정이 정한다.
 * "한 번만 로그인" 과 "아무 데나 통한다" 는 다른 이야기다.
 *
 * <h2>realm 을 함께 확인한다</h2>
 * 클라이언트는 자기 realm 에서만 토큰을 받을 수 있다. 어드민 앱이 포털 realm 으로 로그인해 포털
 * audience 를 얻는 길을 막는다 — realm 격리가 서명키로 지켜지는 것과 같은 이유로, 여기서도
 * 요청이 주장하는 값을 그대로 믿지 않는다.
 */
public class TokenClients {

    private final Map<String, TokenProperties.ClientProperties> clients;

    public TokenClients(Map<String, TokenProperties.ClientProperties> clients) {
        this.clients = clients;
    }

    /**
     * 이 클라이언트가 그 realm 에서 받을 수 있는 audience 목록.
     *
     * <p>모르는 클라이언트나 realm 이 어긋나면 <b>인증 실패</b>로 끝낸다. 400 이 아니라 401 인 이유는,
     * 어떤 clientId 가 존재하는지 응답으로 훑을 수 있게 하지 않기 위해서다.
     */
    public List<String> audiencesOf(Realm realm, String clientId) {
        TokenProperties.ClientProperties client = clients.get(clientId);
        if (client == null || client.getRealm() != realm) {
            throw new AuthenticationFailedException("인증에 실패했습니다.");
        }
        if (client.getAudiences().isEmpty()) {
            // 받아 줄 서비스가 없는 토큰은 만들 이유가 없다. 설정 실수를 부팅이 아니라 여기서 잡는다.
            throw new IllegalStateException("클라이언트에 audience 가 없습니다: token.clients." + clientId);
        }
        return List.copyOf(client.getAudiences());
    }
}
