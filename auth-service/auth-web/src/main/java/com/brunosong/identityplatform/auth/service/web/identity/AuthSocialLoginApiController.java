package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithSocialUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 API. provider 콜백에서 받은 authorizationCode 로 검증·연결·발급을 한 번에 처리한다
 * (가입과 로그인이 같은 경로다).
 *
 * <p><b>고객 realm 호스트에서만 뜬다.</b> 다른 로그인과 달리 이 경로는 처음 들어온 소셜 계정에 대해
 * 신원을 새로 만든다(JIT 프로비저닝). 그래서 realm 중립으로 둘 수 없다 — 직원 호스트에 떠 있으면
 * 아무나 소셜 로그인만으로 직원 신원을 만들 수 있다. 직원 계정은 운영자가 {@code /api/auth/employee/register}
 * 로만 만든다. 예전에는 이 게이팅 없이 컨트롤러 상수로 {@code SubjectType.CUSTOMER} 를 박아뒀는데,
 * 그러면 컨트롤러 자체는 직원 호스트에도 떠서 고객 신원으로 직원 realm 토큰이 나갔다.
 *
 * <p>주체 유형은 상수가 아니라 호스트 설정({@link AuthenticationRealm})에서 온다 — 위 조건 때문에
 * 여기선 항상 CUSTOMER 지만, realm 을 정하는 곳을 설정 한 군데로 모아둔다.
 *
 * <p>다른 로그인 엔드포인트와 같이 토큰을 쿠키로 내린다. 예전에는 이 API 만 본문으로 토큰을 돌려줘서,
 * 같은 서비스 안에서 로그인 방식마다 토큰 전송이 달랐다.
 *
 * <p>provider 검증은 호스트가 제공하는 어댑터가 수행한다(미제공 호스트에선 소셜 미지원).
 */
@RestController
@RequestMapping("/api/auth/login/social")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "authorization.realm", havingValue = "CUSTOMER")
public class AuthSocialLoginApiController {

    private final AuthenticateWithSocialUseCase authenticateWithSocial;
    private final AuthCookies authCookies;
    private final AuthenticationRealm authenticationRealm;

    @PostMapping
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticateWithSocial.authenticate(new SocialAuthCommand(
                authenticationRealm.subjectType(), request.provider(), request.authorizationCode()));
        authCookies.write(response, result.tokens());
        return new LoginResponse(result.subjectId(),
                result.subjectType() == null ? null : result.subjectType().name());
    }

    public record LoginRequest(@NotNull SocialProvider provider, @NotBlank String authorizationCode) {
    }

    public record LoginResponse(String subjectId, String subjectType) {
    }
}
