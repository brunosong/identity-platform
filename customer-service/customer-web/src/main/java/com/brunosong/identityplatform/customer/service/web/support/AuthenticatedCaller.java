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
 * 응용·도메인 계층은 그것조차 모른다 — auth 의 토큰 형식이 바뀌어도 고칠 곳은 여기와 auth-client 뿐이다.
 *
 * <h2>realm 확인이 여기 없는 이유</h2>
 * 이 서비스는 포털 realm 하나만 상대하고, 그 사실은 <b>설정</b>에 있다
 * ({@code auth.client.realm}, {@code auth.client.jwks-uri}). 그래서 realm 확인이 코드에서
 * 사라진 것이 아니라 설정으로 옮겨갔다 — {@link AuthTokenVerifier} 가 그 realm 만 통과시킨다.
 *
 * <p>막는 층이 둘이다. JWKS 가 realm 별로 나뉘어 있어 <b>어드민 realm 의 공개키를 아예 갖지 못하므로</b>
 * 어드민 토큰은 서명 검증에서 죽고, 설령 주소를 잘못 가리켰더라도 {@code realm} 클레임 대조에서 걸린다.
 * 둘 다 이 서비스의 코드가 한 줄도 돌기 전이다.
 *
 * <p>직원이 고객을 조회하는 API 는 이 서비스에 두지 않는다. 그런 것이 필요해지면 <b>같은 코드를
 * 어드민 realm 설정으로 한 벌 더 띄우는</b> 것이 표준적인 방법이다 — 리소스 서버는 realm 하나에
 * 속하는 편이 경계가 분명하다. 한 서비스가 두 realm 을 받기 시작하면 그때부터는 다시 코드가
 * 갈라야 하고, 잊으면 뚫린다.
 *
 * <h2>토큰이 답할 수 없는 것</h2>
 * realm 을 확인해도 <b>"이 데이터가 이 사람 것인가"는 남는다.</b> 그것은 이 서비스만 안다 —
 * 컨트롤러가 조회 키를 토큰의 subjectId 로 잡는 것이 그 답이다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedCaller {

    private static final String BEARER_PREFIX = "Bearer ";

    /** auth-client 가 자동설정으로 만들어 준다(auth.client.jwks-uris 가 있을 때). */
    private final AuthTokenVerifier verifier;

    /** 토큰이 유효하면 호출자, 아니면 401. realm 은 보지 않는다. */
    public AuthenticatedToken require(HttpServletRequest request) {
        return verifier.verifyAuthorizationHeader(bearerHeader(request))
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
    }

    private String bearerHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER_PREFIX) ? header : null;
    }
}
