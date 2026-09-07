package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.IssuedTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토큰 재발급 API. refresh 토큰을 받아 새 access/refresh 를 발급한다.
 * 재발급 시점의 권한과 리비전이 다시 실린다.
 *
 * <p>refresh 토큰은 요청 본문으로 받는다 — 쿠키를 쓰지 않는다({@link IssuedTokens} 참고).
 *
 * <p>realm 은 경로가 정한다. refresh 토큰에는 subjectId 만으로 주체를 특정할 수 없고(유일키는
 * 유형+식별자다), 검증도 그 realm 의 공개키로 한다. 다른 realm 을 지목하면 서명 단계에서 걸린다.
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}/token")
@RequiredArgsConstructor
public class AuthTokenApiController {

    private final RefreshTokenUseCase refreshToken;
    private final AuthenticationRealm authenticationRealm;

    @PostMapping("/refresh")
    public RefreshResponse refresh(@PathVariable String realm, @Valid @RequestBody RefreshRequest request) {
        AuthenticationResult result = refreshToken.refresh(
                authenticationRealm.subjectTypeOf(realm), request.refreshToken());

        return new RefreshResponse(result.subjectId(), result.subjectType().name(),
                IssuedTokens.of(result.tokens()));
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RefreshResponse(String subjectId, String subjectType, IssuedTokens tokens) {
    }
}
