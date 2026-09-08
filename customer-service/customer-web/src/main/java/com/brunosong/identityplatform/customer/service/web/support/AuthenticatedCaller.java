package com.brunosong.identityplatform.customer.service.web.support;

import com.brunosong.identityplatform.auth.client.AuthTokenVerifier;
import com.brunosong.identityplatform.auth.client.AuthenticatedToken;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * <b>이 서비스가 auth 와 만나는 유일한 자리.</b>
 *
 * <p>요청에 실려 온 토큰을 검증하고, 통과하면 "누가 부르는가"를 돌려준다. auth 에게 묻지 않는다 —
 * {@link AuthTokenVerifier} 가 JWKS 로 받아둔 공개키로 서명을 직접 확인한다. 그래서 요청마다
 * 왕복이 생기지 않고, auth 가 잠시 죽어도 이미 발급된 토큰은 계속 통과한다.
 *
 * <p>이 클래스 밖에서는 토큰이라는 말이 나오지 않는다. 컨트롤러는 {@link AuthenticatedToken} 만 받고,
 * 응용·도메인 계층은 그것조차 모른다 — auth 의 토큰 형식이 바뀌어도 고칠 곳은 여기와
 * auth-client 뿐이다.
 *
 * <h2>검증하는 것과 하지 않는 것</h2>
 * auth-client 는 서명·만료·용도(access 인지)까지 본다. <b>realm 이 맞는지는 보지 않는다</b> —
 * 두 realm 의 공개키가 같은 JWKS 에 함께 있으므로 직원 토큰도 서명은 통과한다. 어느 realm 을
 * 받아들일지는 서비스마다 다르므로 여기서 정한다. 이 확인을 빠뜨리면 직원 토큰으로 고객 API 가 열린다.
 *
 * <p>그리고 realm 과 권한을 확인해도 <b>"이 데이터가 이 사람 것인가"는 남는다.</b> 그것은 토큰이
 * 답할 수 없고 이 서비스만 안다 — 컨트롤러가 조회 키를 토큰의 subjectId 로 잡는 것이 그 답이다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedCaller {

    private static final String BEARER_PREFIX = "Bearer ";

    /** auth-client 가 자동설정으로 만들어 준다(auth.client.jwks-uri 가 있을 때). */
    private final AuthTokenVerifier verifier;

    /** 토큰이 유효하면 호출자, 아니면 401. realm 은 보지 않는다. */
    public AuthenticatedToken require(HttpServletRequest request) {
        return verifier.verifyAuthorizationHeader(bearerHeader(request))
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
    }

    /** 그 realm 의 호출자만 통과시킨다. 아니면 401 — 있는지 없는지 알려줄 이유가 없다. */
    public AuthenticatedToken requireRealm(HttpServletRequest request, String realm) {
        AuthenticatedToken caller = require(request);
        if (!caller.isRealm(realm)) {
            throw new UnauthorizedException("이 API 를 부를 수 있는 신원이 아닙니다.");
        }
        return caller;
    }

    /**
     * realm 과 권한을 함께 본다.
     *
     * <p>권한은 토큰에 실려 온 값이라 <b>발급 시점의 것</b>이다. 방금 회수한 권한이 이 토큰에서는
     * 아직 살아 있을 수 있다 — 즉시 회수가 필요하면 access 토큰 수명을 짧게 잡아야 한다.
     */
    public AuthenticatedToken requirePermission(HttpServletRequest request, String realm, String permission) {
        AuthenticatedToken caller = requireRealm(request, realm);
        if (!caller.hasPermission(permission)) {
            throw new ForbiddenException("이 작업에 필요한 권한이 없습니다: " + permission);
        }
        return caller;
    }

    private String bearerHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER_PREFIX) ? header : null;
    }
}
