package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.SingleSessionUseCase;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그아웃 API. 토큰 쿠키를 지우고, 단일 세션을 쓰는 호스트에서는 서버측 세션까지 무효화한다.
 *
 * <p><b>무효화 대상은 호출자 자신이다.</b> 예전에는 본문으로 받은 {@code subjectId} 를 그대로 믿고
 * 그 주체의 세션을 끊었다 — 남의 식별자만 알면 누구든 그 사람을 로그아웃시킬 수 있었다.
 * 지금은 호출자의 access 토큰을 검증해 거기 실린 주체만 무효화한다.
 *
 * <p>realm 은 호스트 설정({@code authorization.realm})에서 온다. 한 호스트 프로세스는 자기 realm 만
 * 시행하므로 요청이 정할 값이 아니다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthLogoutApiController {

    /** 단일 세션은 프로퍼티로 켜는 선택 기능이라 없을 수 있다. */
    private final ObjectProvider<SingleSessionUseCase> singleSessionProvider;
    private final AuthenticatedCaller caller;
    private final AuthCookies authCookies;
    private final Realm realm;

    public AuthLogoutApiController(ObjectProvider<SingleSessionUseCase> singleSessionProvider,
                                   AuthenticatedCaller caller,
                                   AuthCookies authCookies,
                                   @Value("${authorization.realm:#{null}}") Realm realm) {
        this.singleSessionProvider = singleSessionProvider;
        this.caller = caller;
        this.authCookies = authCookies;
        this.realm = realm;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Claims claims = caller.require(request);

        SingleSessionUseCase singleSession = singleSessionProvider.getIfAvailable();
        if (singleSession != null) {
            if (realm == null) {
                throw new IllegalStateException(
                        "단일 세션을 켜려면 authorization.realm 이 필요합니다(무효화 대상 realm).");
            }
            singleSession.invalidate(realm, caller.subjectId(claims));
        }

        authCookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
