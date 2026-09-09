package com.brunosong.identityplatform.auth.service.application.identity.token;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Locale;

/**
 * realm 별 발급자 이름({@code iss})을 만든다 — {@code {base}/realms/{realm}}.
 *
 * <h2>왜 realm 마다 다른가</h2>
 * realm 마다 서명키가 다르므로 사실상 서로 다른 발급자다. 발급자 이름도 그렇게 나누면
 * <b>"어느 서버의 어느 realm 인가"를 한 값이 다 말한다.</b> Keycloak·Auth0·Cognito 가 모두 이렇게 한다
 * (Keycloak: {@code https://kc.example.com/realms/portal}).
 *
 * <h2>왜 필요한가 — 서명만으로는 부족한 자리</h2>
 * 서명은 "이 키를 가진 누군가가 만들었다"까지만 말한다. <b>어느 배포의 키인지는 말하지 않는다.</b>
 * staging 의 포털 토큰과 prod 의 포털 토큰은 클레임이 완전히 같아서, 소비 서비스가 JWKS 주소를
 * 잘못 가리키면 staging 계정으로 prod 가 열린다. 그것을 가르는 값이 {@code iss} 뿐이다.
 * (RFC 8725 가 발급자 검증을 요구하는 이유이기도 하다 — cross-JWT confusion.)
 *
 * <h2>주소이기도 하다</h2>
 * 이 규칙 덕분에 {@code iss + "/.well-known/jwks.json"} 이 그 realm 의 JWKS 주소가 된다.
 * 그래서 소비 서비스는 발급자 하나만 설정하면 되고, 키를 어디서 받을지는 유도된다
 * (OIDC 의 {@code {issuer}/.well-known/openid-configuration} 과 같은 발상이다).
 */
public final class RealmIssuers {

    private final String baseUrl;

    public RealmIssuers(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            // 발급자 없이 뜨면 모든 토큰이 발급자 없는 토큰이 된다. 부팅에서 막는다.
            throw new IllegalStateException("token.issuer 가 필요합니다.");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** realm 은 경로에서 소문자다 — JWKS 경로와 같은 표기를 써야 두 값이 어긋나지 않는다. */
    public String of(Realm realm) {
        return baseUrl + "/realms/" + realm.name().toLowerCase(Locale.ROOT);
    }
}
