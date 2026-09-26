package com.brunosong.identityplatform.auth.service.web.wellknown;

import com.brunosong.identityplatform.auth.service.application.identity.token.RealmIssuers;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * realm 의 발급자 메타데이터 - {@code /realms/{realm}/.well-known/openid-configuration}.
 *
 * <h2>왜 있나</h2>
 * 읽는 쪽이 <b>발급자 주소 하나만</b> 알면 되게 하려고 있다. 표준 리소스 서버(Spring Security 의
 * {@code issuer-uri}, 그 밖의 OIDC 라이브러리)는 이 문서를 먼저 받아 {@code jwks_uri} 를 읽고,
 * 거기서 공개키를 가져간다. 그래서 소비 서비스 설정이 이렇게 줄어든다.
 *
 * <pre>
 * spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8080/realms/portal
 * </pre>
 *
 * <p>주소가 규칙으로 이어져 있다는 것이 요점이다. {@code iss} 가 곧 이 문서의 위치이고, 이 문서가
 * 나머지 주소를 가리킨다. 그래서 realm 을 옮기거나 늘려도 소비 서비스는 한 줄만 바꾼다.
 *
 * <h2>적은 것은 전부 지금 도는 것이다</h2>
 * 이 문서를 읽는 것은 사람이 아니라 기계다. 지원하지 않는 값을 적으면 그것을 믿고 보낸 요청이
 * 400 으로 돌아오고, 있는 엔드포인트를 빼면 라이브러리가 로그인을 시작하지 못한다. 그래서
 * <b>여기 있는 값은 컨트롤러가 실제로 받는 것과 한 글자씩 맞춰 둔다.</b>
 * 받는 것은 {@code code} 하나이고, 토큰으로 바꾸는 방법은 {@code authorization_code} 와
 * {@code refresh_token} 둘이다. 재발급이 토큰 엔드포인트로 들어오면서 이 문서에 적을 수 있게
 * 됐다. 우리 주소에 따로 있을 때는 표준 자리가 아니라 적을 수가 없었고, 그래서 표준 클라이언트는
 * 재발급하는 길을 알 방법이 없었다.
 *
 * <p>{@code code_challenge_methods_supported} 를 적어두는 이유는 PKCE 가 선택이 아니기 때문이다.
 * 빼두면 클라이언트가 지원하지 않는 줄 알고 {@code code_challenge} 없이 보내는데, 그 요청은
 * 전부 거절된다.
 *
 * <p>{@code prompt_values_supported} 도 같은 이유로 적는다. 아는 값은 {@code none} 하나이고,
 * 나머지는 무시하지 않고 거절한다. 조용히 무시하면 {@code prompt=login} 으로 재인증을 요구한 쪽이
 * 다시 물었다고 믿는데 실제로는 세션이 그대로 통과한다.
 *
 * <h2>ID 토큰은 없다</h2>
 * {@code id_token} 을 발급하지 않는다. 그래서 이 문서는 OIDC RP 가 아니라 OAuth2 클라이언트가
 * 읽을 문서이고, {@code userinfo_endpoint} 처럼 ID 토큰에 딸린 자리도 비어 있다.
 * {@code id_token_signing_alg_values_supported} 만 남겨둔 것은 리소스 서버가 서명 알고리즘을
 * 읽을 표준 자리가 이것뿐이어서다.
 *
 * <p>인증이 필요 없다. 여기 담긴 것은 모두 공개 정보다.
 */
@RestController
@RequiredArgsConstructor
public class OpenIdConfigurationApiController {

    /** 자주 바뀌지 않는다. 바뀌는 것은 이 문서가 가리키는 JWKS 쪽이다. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final AuthenticationRealm authenticationRealm;
    private final RealmIssuers issuers;

    @GetMapping("/realms/{realm}/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> configuration(@PathVariable String realm) {
        Realm resolved = authenticationRealm.of(realm);   // 모르는 realm 은 404
        String issuer = issuers.of(resolved);

        Map<String, Object> document = new LinkedHashMap<>();
        // 받아간 쪽은 이 값이 자기가 요청한 주소와 같은지 확인한다. 다르면 거부해야 한다.
        // 남의 발급자 문서를 우리 주소에 놓아두는 공격을 막는 확인이다.
        document.put("issuer", issuer);
        document.put("authorization_endpoint", issuer + "/auth");
        document.put("token_endpoint", issuer + "/token");
        document.put("end_session_endpoint", issuer + "/logout");
        document.put("jwks_uri", issuer + "/.well-known/jwks.json");
        document.put("response_types_supported", List.of("code"));
        document.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        // PKCE 는 필수이고 plain 은 받지 않는다.
        document.put("code_challenge_methods_supported", List.of("S256"));
        // 아는 prompt 는 none 하나다. login, consent 를 보내면 400 이다.
        document.put("prompt_values_supported", List.of("none"));
        // 시크릿 없는 앱은 none, 있는 앱은 본문에 client_secret 을 싣는다. Basic 헤더는 받지 않는다.
        document.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_post"));
        document.put("subject_types_supported", List.of("public"));
        document.put("id_token_signing_alg_values_supported", List.of("RS256"));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(document);
    }
}
