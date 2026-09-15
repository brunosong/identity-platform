package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Map;

/**
 * realm 마다 그 토큰이 향하는 <b>시스템</b>. 토큰의 {@code aud} 가 된다.
 *
 * <h2>시스템은 마이크로서비스의 집합이다</h2>
 * {@code shop} 안에 {@code customer-service} 와 {@code order-service} 가 있다. 그 안에 서비스가
 * 몇 개인지는 토큰도 앱도 모르고 DB({@code authz_service})만 안다. 그래서 서비스를 하나 붙여도
 * {@code aud} 는 그대로이고, auth 를 재배포할 일이 없다.
 *
 * <h2>왜 앱(clientId)이 고르지 않나</h2>
 * 한때 로그인 요청이 {@code clientId} 를 함께 보내고, 그 앱이 속한 시스템을 {@code aud} 로 썼다.
 * 그런데 realm 과 시스템이 1:1 이라 <b>앱은 새 정보를 더하지 않았다.</b> realm 은 이미 경로에
 * 있으므로({@code /realms/portal/login}) 시스템은 거기서 바로 나온다.
 *
 * <p>대신 입력이 둘이 되면서 <b>어긋날 수 있는 자리</b>가 생겼다. 포털 앱으로 어드민 realm 토큰을
 * 요청하는 것 같은 실패 모드는 {@code clientId} 가 없으면 성립하지 않는다. 자기가 만든 문제를
 * 자기가 막고 있었던 셈이다. realm 격리를 실제로 지키는 것은 서명키다.
 *
 * <h2>한 realm 에 시스템이 둘이 되면</h2>
 * 그때는 경로만으로 정할 수 없으므로 고를 값이 다시 필요하다. {@code clientId} 든 다른 이름이든
 * 그 시점에 들인다. 지금 미리 두지 않는 이유는, 값이 있어도 고를 것이 하나뿐이라 아무 판단도
 * 하지 않기 때문이다.
 */
public class RealmSystems {

    private final Map<Realm, String> byRealm;

    public RealmSystems(Map<Realm, String> byRealm) {
        this.byRealm = byRealm;
    }

    /** 이 realm 의 토큰이 향하는 시스템. 부팅에서 이미 확인했으므로 여기서 비어 있을 수 없다. */
    public String of(Realm realm) {
        String system = byRealm.get(realm);
        if (system == null) {
            throw new IllegalStateException("realm 에 system 이 없습니다: token.realms." + realm);
        }
        return system;
    }
}
