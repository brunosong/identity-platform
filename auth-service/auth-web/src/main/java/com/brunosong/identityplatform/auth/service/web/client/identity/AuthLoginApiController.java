package com.brunosong.identityplatform.auth.service.web.client.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
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
 * 아이디/비밀번호 로그인 API.
 *
 * <p>어느 realm 의 자격증명을 확인할지는 경로가 정한다({@link AuthenticationRealm}). 이 서비스가 두 realm 을
 * 모두 담당하므로 설정으로는 알 수 없다. realm 을 잘못 지목해도 그 realm 에 계정이 없으면 인증이
 * 성립하지 않는다 — 조회가 realm 으로 좁혀져 있기 때문이다.
 *
 * <p>토큰은 응답 본문으로 내린다. 이후 요청은 {@code Authorization: Bearer} 로 싣는다.
 * 쿠키를 쓰지 않는 이유와 그 대가는 {@link IssuedTokens} 에 적어 뒀다.
 *
 * <p>실패 매핑은 {@code AuthApiExceptionHandler} 가 맡는다 — 자격증명 실패는 401 이고,
 * 아이디 미존재와 비밀번호 불일치는 같은 메시지로 나간다(계정 열거 방지).

 * <p>{@code clientId} 는 <b>어느 앱이 요청했는가</b>다. 그 값이 토큰의 {@code aud} 를 정하고,
 * 나열되지 않은 서비스는 그 토큰을 거부한다. 모르는 값이거나 realm 이 어긋나면 401 이다 —
 * 400 이 아닌 이유는 어떤 clientId 가 존재하는지 응답으로 훑을 수 없게 하기 위해서다.
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}/login")
@RequiredArgsConstructor
public class AuthLoginApiController {

    private final AuthenticateWithPasswordUseCase authenticateWithPassword;
    private final AuthenticationRealm authenticationRealm;

    @PostMapping
    public LoginResponse login(@PathVariable String realm, @Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authenticateWithPassword.authenticate(new PasswordAuthCommand(
                authenticationRealm.of(realm), request.loginId(), request.password(), request.clientId()));

        return new LoginResponse(result.subjectId(), result.realm().name(),
                IssuedTokens.of(result.tokens()));
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password,
                               @NotBlank String clientId) {
    }

    public record LoginResponse(String subjectId, String realm, IssuedTokens tokens) {
    }
}
