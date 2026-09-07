package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.SingleSessionUseCase;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그아웃 API. 단일 세션을 쓰는 설정에서는 서버측 세션을 무효화한다.
 *
 * <p><b>무효화 대상은 호출자 자신이다.</b> 예전에는 본문으로 받은 {@code subjectId} 를 그대로 믿고
 * 그 주체의 세션을 끊었다 — 남의 식별자만 알면 누구든 그 사람을 로그아웃시킬 수 있었다.
 * 지금은 호출자의 access 토큰을 검증해 거기 실린 주체만 무효화한다.
 *
 * <p>무효화할 realm 도 그 토큰에서 읽는다. 이 서비스가 두 realm 을 담당하므로 설정으로는 알 수 없고,
 * 요청이나 경로가 정하게 두면 남의 realm 세션을 지목할 수 있다. 서명된 토큰이 유일하게 믿을 수 있는 출처다.
 *
 * <p>지울 쿠키는 없다 — 토큰은 본문으로 나가고 호출자가 보관한다. <b>토큰 폐기는 호출자 몫이다.</b>
 * 단일 세션을 켜지 않았다면 이 호출 뒤에도 발급된 access 토큰은 만료까지 유효하다(무상태 JWT 의 성질).
 * 즉시 회수가 필요하면 access TTL 을 짧게 두거나 별도의 폐기 목록이 있어야 한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthLogoutApiController {

    /** 단일 세션은 프로퍼티로 켜는 선택 기능이라 없을 수 있다. */
    private final ObjectProvider<SingleSessionUseCase> singleSessionProvider;
    private final AuthenticatedCaller caller;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Claims claims = caller.require(request);

        SingleSessionUseCase singleSession = singleSessionProvider.getIfAvailable();
        if (singleSession != null) {
            singleSession.invalidate(caller.realmOf(claims), caller.subjectId(claims));
        }
        return ResponseEntity.noContent().build();
    }
}
