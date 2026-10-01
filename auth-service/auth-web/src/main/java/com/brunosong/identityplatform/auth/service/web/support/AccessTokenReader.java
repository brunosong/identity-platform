package com.brunosong.identityplatform.auth.service.web.support;

import com.brunosong.identityplatform.auth.service.application.identity.token.RealmIssuers;
import com.brunosong.identityplatform.auth.service.application.identity.token.RealmSigningKeys;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * auth 가 발급한 access 토큰을 검증(서명·만료)하고 claims 를 읽는다. 무효/만료 토큰은 빈 값으로 다룬다
 * (예외 대신 Optional.empty — 비로그인/만료를 정상 흐름으로 처리).
 *
 * <h2>realm 을 먼저 정하고, 그 realm 의 키로만 검증한다</h2>
 * 검증 키를 {@code kid} 로 고르지 않는다. 이 서비스는 두 realm 의 키를 <b>다</b> 쥐고 있어서, kid 를
 * 따라가면 어느 realm 의 토큰이든 서명이 통과한다. 그러면 "이 토큰이 어느 realm 것인가"를 다시
 * 클레임에 물어봐야 하는데, 클레임은 토큰이 스스로 하는 주장이다.
 *
 * <p>realm 을 먼저 받아 그 realm 의 공개키 하나로만 검증하면 <b>realm 을 서명이 증명한다.</b>
 * 다른 realm 의 토큰은 여기서 죽는다 — 클레임을 읽어보기도 전에.
 *
 * <p>호출자가 realm 을 정한다고 해서 realm 을 사칭할 수 있는 것은 아니다. 경로는 realm 을
 * <b>주장</b>하는 것이 아니라 <b>검증 키를 고를</b> 뿐이라, 잘못 지목하면 그 키로 검증에 실패한다.
 * (Keycloak 도 모든 엔드포인트가 {@code /realms/{realm}/...} 이고 realm 을 토큰보다 먼저 정한다.)
 *
 * <p>{@code iss} 도 함께 대조한다. 같은 realm 이라도 <b>다른 배포</b>(staging 등)가 발급한 토큰은
 * 받지 않는다 — 그런 토큰은 서명 알고리즘도 realm 도 같아서, 발급자 이름만이 둘을 가른다.
 *
 * <h2>{@code aud} 는 여기서 보지 않는다</h2>
 * 여기를 쓰는 곳은 토큰의 주인에 대한 일을 한다(운영 화면의 로그인 확인 등). 고객 시스템 토큰
 * ({@code aud=[shop]})으로도 자기 정보는 볼 수 있어야 하니 받는 쪽을 따지지 않는다. Keycloak 의 userinfo,
 * logout 도 audience 를 따지지 않는다. auth 자신의 자원을 바꾸는 관리 API 는 {@code aud} 까지 보는데,
 * 그 검증은 스프링 시큐리티 체인이 한다({@link SecurityConfiguration#masterJwtDecoder}).
 */
@Component
public class AccessTokenReader {

    private final RealmSigningKeys signingKeys;
    private final RealmIssuers issuers;

    public AccessTokenReader(RealmSigningKeys signingKeys, RealmIssuers issuers) {
        this.signingKeys = signingKeys;
        this.issuers = issuers;
    }

    /**
     * 그 realm 의 공개키와 발급자로 검증한다. 다른 realm 이나 다른 배포의 토큰이면 빈 값이다.
     *
     * <p><b>{@code aud} 는 보지 않는다.</b> client 채널이 쓰는 경로이고, 그쪽은 토큰의 주인에
     * 대한 API 라 어느 앱이 받은 토큰이든 정상이다.
     */
    public Optional<Claims> read(Realm realm, String token) {
        return parse(realm, token);
    }

    private Optional<Claims> parse(Realm realm, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKeys.of(realm).publicKey())
                    .requireIssuer(issuers.of(realm))
                    .build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
