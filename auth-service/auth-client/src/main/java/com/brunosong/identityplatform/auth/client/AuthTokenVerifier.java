package com.brunosong.identityplatform.auth.client;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.security.Key;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 다른 서비스가 auth 의 access 토큰을 검증하는 진입점.
 *
 * <p>서명 검증은 auth 에 묻지 않고 여기서 한다. 공개키만 있으면 되고, 그 키는 JWKS 로 받는다
 * ({@link JwksKeySource}). 그래서 요청마다 auth 로 왕복이 생기지 않고, auth 가 잠시 죽어도
 * 이미 발급된 토큰은 계속 통과한다.
 *
 * <p>검증하는 것은 넷이다:
 * <ol>
 *   <li><b>서명</b> — 헤더의 {@code kid} 로 고른 공개키로 확인한다.</li>
 *   <li><b>만료</b> — jjwt 가 {@code exp} 를 본다.</li>
 *   <li><b>용도</b> — {@code type=access} 여야 한다. refresh 토큰을 access 처럼 쓰지 못하게 막는다.</li>
 *   <li><b>realm 존재</b> — 클레임에 realm 이 있어야 한다.</li>
 * </ol>
 *
 * <p><b>realm 이 맞는지는 검증하지 않는다.</b> 두 realm 의 공개키가 같은 JWKS 에 함께 있으므로 직원
 * 토큰도 서명은 통과한다. 어느 realm 을 받아들일지는 서비스마다 다르므로 호출자가 정한다 —
 * {@link AuthenticatedToken#isRealm(String)} 으로 확인하거나 {@link #verify(String, String)} 을 쓴다.
 * 이 구분을 잊으면 직원 토큰으로 고객 API 가 열린다.
 *
 * <p>무효한 토큰은 예외가 아니라 빈 값이다. 비로그인과 만료는 정상 흐름이지 오류가 아니다.
 */
public class AuthTokenVerifier {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REALM_CLAIM = "realm";
    private static final String SUBJECT_ID_CLAIM = "userId";
    private static final String EMAIL_CLAIM = "email";
    private static final String PERMISSIONS_CLAIM = "authLs";
    private static final String REVISION_CLAIM = "rbacRev";

    private final JwksKeySource keySource;

    public AuthTokenVerifier(JwksKeySource keySource) {
        this.keySource = keySource;
    }

    /** 서명·만료·용도를 확인한다. realm 확인은 호출자 몫이다. */
    public Optional<AuthenticatedToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(this::keyFor)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }

        if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class))) {
            return Optional.empty();
        }
        String realm = claims.get(REALM_CLAIM, String.class);
        if (realm == null || realm.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedToken(
                realm,
                claims.get(SUBJECT_ID_CLAIM, String.class),
                claims.get(EMAIL_CLAIM, String.class),
                permissionsOf(claims),
                revisionOf(claims)));
    }

    /** 그 realm 의 토큰일 때만 통과시킨다. 한 realm 만 상대하는 서비스가 쓴다. */
    public Optional<AuthenticatedToken> verify(String token, String requiredRealm) {
        return verify(token).filter(t -> t.isRealm(requiredRealm));
    }

    /** {@code Authorization: Bearer ...} 헤더 값에서 바로 검증한다. */
    public Optional<AuthenticatedToken> verifyAuthorizationHeader(String headerValue) {
        if (headerValue == null || !headerValue.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return verify(headerValue.substring("Bearer ".length()).trim());
    }

    private Key keyFor(Header header) {
        Object kid = header.get("kid");
        return kid == null ? null : keySource.find(kid.toString());
    }

    /** 권한은 쉼표로 이어 붙인 한 문자열이다 — 토큰 크기를 줄이려고 배열 대신 그렇게 싣는다. */
    private static List<String> permissionsOf(Claims claims) {
        String raw = claims.get(PERMISSIONS_CLAIM, String.class);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static long revisionOf(Claims claims) {
        Number revision = claims.get(REVISION_CLAIM, Number.class);
        return revision == null ? 0L : revision.longValue();
    }
}
