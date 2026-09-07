package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아이디/비밀번호 로그인 API.
 *
 * <p>자격증명을 받아 인증하고 발급된 토큰을 쿠키로 실어 내려준다. 토큰 전송(쿠키 이름·만료·속성)은
 * {@link AuthCookies} 한곳에서 정한다 — 로그인·재발급·로그아웃이 같은 규칙을 써야 한다.
 *
 * <p>응답 본문에는 토큰을 담지 않는다. 쿠키로 이미 내려갔고, 본문에도 실으면 스크립트가 읽을 수 있는
 * 자리에 한 벌이 더 생긴다({@code httpOnly} 를 두는 이유가 사라진다).
 *
 * <p>실패 매핑은 {@code AuthApiExceptionHandler} 가 맡는다 — 자격증명 실패는 401 이고,
 * 아이디 미존재와 비밀번호 불일치는 같은 메시지로 나간다(계정 열거 방지).
 *
 * <p>어느 realm 의 자격증명을 확인할지는 {@link AuthenticationRealm} 이 정한다. 요청 본문은 realm 을 담지
 * 않는다 — loginId 만 받아 전역에서 찾으면 상대 realm 계정으로도 로그인이 통과한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthLoginApiController {

    private final AuthenticateWithPasswordUseCase authenticateWithPassword;
    private final AuthCookies authCookies;
    private final AuthenticationRealm authenticationRealm;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticateWithPassword.authenticate(new PasswordAuthCommand(
                authenticationRealm.subjectType(), request.loginId(), request.password()));
        authCookies.write(response, result.tokens());
        return new LoginResponse(result.subjectId(),
                result.subjectType() == null ? null : result.subjectType().name());
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    }

    public record LoginResponse(String subjectId, String subjectType) {
    }
}
