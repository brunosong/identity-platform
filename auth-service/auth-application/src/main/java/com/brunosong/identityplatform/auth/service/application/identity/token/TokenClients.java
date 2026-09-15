package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 토큰을 받아 갈 앱들. 어느 앱이 요청했고, 그 토큰이 어느 <b>시스템</b>을 향하는가.
 *
 * <h2>왜 aud 가 필요한가</h2>
 * 전에는 토큰에 {@code aud} 가 없었다. 그러면 <b>발급자만 맞으면 누구든 받아들인다</b>. 포털 토큰
 * 하나가 포털의 모든 것에 통한다. 침해된 한 곳으로 들어온 사용자 토큰을 다른 곳에 그대로 재생할 수
 * 있고, 늘어날수록 <b>가장 약한 하나가 realm 전체의 보안 수준</b>이 된다.
 *
 * <h2>aud 는 시스템이지 서비스가 아니다</h2>
 * 전에는 여기에 서비스 이름을 나열했다({@code [customer-service, order-service]}). 그러면 서비스를
 * 하나 붙일 때마다 <b>auth 를 재배포</b>해야 했고, 이미 발급된 access 토큰에는 새 서비스가 없어
 * 만료돼 재발급될 때까지(기본 2시간) 그 서비스에 닿지 못했다. 내부 분해가 토큰에 새어나간 것이다.
 *
 * <p>이제 시스템 하나를 가리킨다. 그 시스템에 서비스가 몇 개인지는 토큰도 앱도 모르고,
 * DB({@code authz_service})만 안다. 서비스를 붙여도 aud 는 그대로다.
 *
 * <h2>시스템을 여럿 적지 않는다</h2>
 * 통합 로그인은 <b>세션</b>이 맡는 일이지 aud 에 여럿을 나열해서 될 일이 아니다. 표준 흐름은 한 번
 * 인증해 세션을 만들고, 시스템마다 토큰을 따로 받는 것이다(OIDC, Keycloak). 사용자 눈에는 로그인
 * 한 번이고 토큰은 여럿이다.
 *
 * <p>나열하면 잃는 것이 분명하다. 시스템은 보통 팀도 배포 주기도 다른데 그 경계가 없어지고,
 * 시스템을 늘릴 때 설정을 고쳐야 하는 문제가 한 층 위로 옮겨갈 뿐이다.
 *
 * <h2>realm 을 함께 확인한다</h2>
 * 앱은 자기 realm 에서만 토큰을 받을 수 있다. 어드민 앱이 포털 realm 으로 로그인해 포털 시스템의
 * 토큰을 얻는 길을 막는다. realm 격리가 서명키로 지켜지는 것과 같은 이유로, 여기서도 요청이
 * 주장하는 값을 그대로 믿지 않는다.
 */
public class TokenClients {

    private final Map<String, TokenProperties.ClientProperties> clients;

    public TokenClients(Map<String, TokenProperties.ClientProperties> clients) {
        this.clients = clients;
    }

    /**
     * 이 앱이 그 realm 에서 상대하는 시스템. 토큰의 {@code aud} 가 된다.
     *
     * <p>모르는 앱이거나 realm 이 어긋나면 <b>인증 실패</b>로 끝낸다. 400 이 아니라 401 인 이유는,
     * 어떤 clientId 가 존재하는지 응답으로 훑을 수 있게 하지 않기 위해서다.
     */
    public String systemOf(Realm realm, String clientId) {
        TokenProperties.ClientProperties client = clients.get(clientId);
        if (client == null || client.getRealm() != realm) {
            throw new AuthenticationFailedException("인증에 실패했습니다.");
        }
        if (!StringUtils.hasText(client.getSystem())) {
            // 향할 곳이 없는 토큰은 만들 이유가 없다. 부팅에서 이미 걸리지만 여기서도 막는다.
            throw new IllegalStateException("앱에 system 이 없습니다: token.clients." + clientId);
        }
        return client.getSystem();
    }
}
