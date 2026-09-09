package com.brunosong.identityplatform.auth.service.web.token;

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
 * realm 의 발급자 메타데이터 — {@code /realms/{realm}/.well-known/openid-configuration}.
 *
 * <h2>왜 있나</h2>
 * 소비 서비스가 <b>발급자 주소 하나만</b> 알면 되게 하려고 있다. 표준 리소스 서버(Spring Security 의
 * {@code issuer-uri}, 그 밖의 OIDC 라이브러리)는 이 문서를 먼저 받아 {@code jwks_uri} 를 읽고,
 * 거기서 공개키를 가져간다. 그래서 소비 서비스 설정이 이렇게 줄어든다.
 *
 * <pre>
 * spring.security.oauth2.resourceserver.jwt.issuer-uri: http://localhost:8080/realms/portal
 * </pre>
 *
 * <p>주소가 규칙으로 이어져 있다는 것이 요점이다 — {@code iss} 가 곧 이 문서의 위치이고, 이 문서가
 * JWKS 위치를 가리킨다. 그래서 realm 을 옮기거나 늘려도 소비 서비스는 한 줄만 바꾼다.
 *
 * <h2>이 문서는 <b>일부</b>다</h2>
 * {@code authorization_endpoint} 와 {@code token_endpoint} 를 싣지 않는다. 이 서비스는 OIDC 인가
 * 서버가 아니기 때문이다 — authorization code 플로우도, 클라이언트(client_id/redirect_uri) 개념도,
 * ID 토큰도 없다. 없는 엔드포인트를 문서에 적으면 그것을 믿고 호출한 쪽이 404 를 만난다.
 * <b>기계가 읽는 문서에 거짓을 적지 않는다.</b>
 *
 * <p>여기 싣는 것은 "이 발급자의 토큰을 검증하려면 무엇을 알아야 하는가" 뿐이다 — 발급자 이름,
 * 공개키 위치, 서명 알고리즘. 그것이 리소스 서버가 실제로 쓰는 전부이기도 하다.
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
        // 받아간 쪽은 이 값이 자기가 요청한 주소와 같은지 확인한다. 다르면 거부해야 한다 —
        // 남의 발급자 문서를 우리 주소에 놓아두는 공격을 막는 확인이다.
        document.put("issuer", issuer);
        document.put("jwks_uri", issuer + "/.well-known/jwks.json");
        document.put("id_token_signing_alg_values_supported", List.of("RS256"));
        document.put("subject_types_supported", List.of("public"));
        document.put("response_types_supported", List.of("token"));
        document.put("grant_types_supported", List.of("password", "refresh_token"));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePublic())
                .body(document);
    }
}
