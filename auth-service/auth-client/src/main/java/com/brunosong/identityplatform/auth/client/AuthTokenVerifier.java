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
 * <h2>realm 은 설정이 정한다</h2>
 * 이 검증기는 <b>한 realm 만 통과시킨다.</b> 그 realm 은 생성할 때 정해지고 요청이 바꿀 수 없다.
 * 그래서 소비 서비스의 컨트롤러가 realm 을 따로 확인할 필요가 없다 — 확인이 사라진 것이 아니라
 * <b>코드에서 설정으로 옮겨간 것</b>이다.
 *
 * <p>막는 것은 <b>JWKS 분리</b>다. realm 마다 주소가 다르므로 이 서비스는 다른 realm 의 공개키를
 * 애초에 갖지 못하고, 그런 토큰은 {@code kid} 를 찾지 못해 서명 검증에서 죽는다.
 *
 * <p>토큰에 {@code realm} 클레임은 없다. realm 마다 서명키가 다르므로 <b>어느 키로 검증됐는지가 곧
 * realm</b> 이고, 클레임은 그것을 한 번 더 적어둔 것이었다.
 *
 * <p><b>아직 비어 있는 자리:</b> {@code jwks-uri} 를 다른 realm 주소로 잘못 적으면 그 realm 의 토큰이
 * 통과한다 — 조용히, fail-open 으로. 예전에는 클레임 대조가 그것을 잡았다. 표준 자리인 {@code iss}
 * 검증으로 되돌려 놓아야 한다(RFC 8725 도 발급자 검증을 요구한다).
 *
 * <h2>검증하는 것</h2>
 * <ol>
 *   <li><b>서명</b> — 헤더의 {@code kid} 로 고른 공개키로 확인한다. 이 서비스가 가진 키는 자기 realm 것뿐이다.</li>
 *   <li><b>만료</b> — jjwt 가 {@code exp} 를 본다.</li>
 *   <li><b>용도</b> — {@code type=access} 여야 한다. refresh 토큰을 access 처럼 쓰지 못하게 막는다.</li>
 * </ol>
 *
 * <p><b>여기서 답하지 못하는 것이 하나 남는다</b> — "이 데이터가 이 사람 것인가". 그것은 토큰이
 * 답할 수 없고 소비 서비스만 안다. 조회 키를 {@link AuthenticatedToken#subjectId()} 로 잡는 것이 그 답이다.
 *
 * <p>무효한 토큰은 예외가 아니라 빈 값이다. 비로그인과 만료는 정상 흐름이지 오류가 아니다.
 */
public class AuthTokenVerifier {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String PERMISSIONS_CLAIM = "authLs";
    private static final String REVISION_CLAIM = "rbacRev";

    private final JwksKeySource keySource;
    /** 이 검증기가 상대하는 realm. 토큰에서 읽는 값이 아니라 설정이 정하는 값이다. */
    private final String realm;

    public AuthTokenVerifier(JwksKeySource keySource, String realm) {
        if (realm == null || realm.isBlank()) {
            // 검증에 쓰이지는 않지만, 이 서비스가 어느 realm 을 상대하는지 밝히지 않은 채 뜨면
            // jwks-uri 를 잘못 가리켰을 때 아무도 알아채지 못한다.
            throw new IllegalStateException("auth.client.realm 이 필요합니다.");
        }
        this.keySource = keySource;
        this.realm = realm;
    }

    /** 이 서비스가 상대하는 realm. */
    public String realm() {
        return realm;
    }

    /** 서명·만료·용도를 확인한다. 통과하면 호출자, 아니면 빈 값. */
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
        return Optional.of(new AuthenticatedToken(
                // 토큰이 아니라 설정에서 온다 — realm 은 서명이 증명했다.
                realm,
                claims.getSubject(),
                permissionsOf(claims),
                revisionOf(claims)));
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
