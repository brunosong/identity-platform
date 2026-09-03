package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithSocialUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 API. provider 콜백에서 받은 authorizationCode 로 검증·연결·발급을 한 번에 처리한다
 * (가입과 로그인이 같은 경로다). 대상 주체는 고객이다.
 *
 * <p>다른 로그인 엔드포인트와 같이 토큰을 쿠키로 내린다. 예전에는 이 API 만 본문으로 토큰을 돌려줘서,
 * 같은 서비스 안에서 로그인 방식마다 토큰 전송이 달랐다.
 *
 * <p>provider 검증은 호스트가 제공하는 어댑터가 수행한다(미제공 호스트에선 소셜 미지원).
 */
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class AuthSocialLoginApiController {

    private final AuthenticateWithSocialUseCase authenticateWithSocial;
    private final AuthCookies authCookies;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticateWithSocial.authenticate(
                new SocialAuthCommand(SubjectType.CUSTOMER, request.provider(), request.authorizationCode()));
        authCookies.write(response, result.tokens());
        return new LoginResponse(result.subjectId(),
                result.subjectType() == null ? null : result.subjectType().name());
    }

    public record LoginRequest(@NotNull SocialProvider provider, @NotBlank String authorizationCode) {
    }

    public record LoginResponse(String subjectId, String subjectType) {
    }
}
