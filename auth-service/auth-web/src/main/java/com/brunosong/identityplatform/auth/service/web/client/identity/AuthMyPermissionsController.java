package com.brunosong.identityplatform.auth.service.web.client.identity;

import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticatedCaller;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인 사용자 본인의 권한 조회 API. 프론트 메뉴/버튼 제어용이다.
 *
 * <p>auth 가 발급한 accessToken 의 권한 claim 을 auth 가 직접 읽어 돌려준다. 비로그인/무효 토큰이면
 * 빈 목록을 준다. 여기서 401 을 내면 로그인 화면조차 이 API 로 오류를 받게 된다.
 *
 * <p>realm 은 경로가 정한다. 그 realm 의 공개키로만 검증하므로 다른 realm 의 토큰은 무효 토큰과 같다 —
 * 빈 목록이 나간다. 경로에 적은 realm 이 곧 응답의 realm 인 이유는, 그것이 서명으로 확인된 값이기
 * 때문이다(토큰이 클레임으로 주장하는 값이 아니다).
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}")
@RequiredArgsConstructor
public class AuthMyPermissionsController {

    private final AuthenticatedCaller caller;
    private final AuthenticationRealm authenticationRealm;

    @GetMapping("/my-permissions")
    public MyPermissionsResponse myPermissions(@PathVariable String realm, HttpServletRequest request) {
        Realm resolved = authenticationRealm.of(realm);
        return caller.read(resolved, request)
                .map(claims -> new MyPermissionsResponse(resolved.name(), caller.permissionsOf(claims)))
                // 토큰이 없거나 이 realm 것이 아니면 realm 도 비운다 — 로그인한 것처럼 보이면 안 된다.
                .orElseGet(() -> new MyPermissionsResponse(null, List.of()));
    }

    public record MyPermissionsResponse(String realm, List<String> permissions) {
    }
}
