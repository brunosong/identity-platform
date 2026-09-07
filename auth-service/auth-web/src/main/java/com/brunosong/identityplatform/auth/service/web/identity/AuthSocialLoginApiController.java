package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.SubjectRealm;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithSocialUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.SocialAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.identity.SocialProvider;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.IssuedTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 API. provider 콜백에서 받은 authorizationCode 로 검증·연결·발급을 한 번에 처리한다
 * (가입과 로그인이 같은 경로다).
 *
 * <p><b>고객 realm 에서만 열린다.</b> 다른 로그인과 달리 이 경로는 처음 들어온 소셜 계정에 대해 신원을
 * 새로 만든다(JIT 프로비저닝). 그래서 realm 중립으로 둘 수 없다 — 직원 realm 에서 열려 있으면 아무나
 * 소셜 로그인만으로 직원 신원을 만들 수 있다. 직원 계정은 운영자가
 * {@code /api/auth/employee/register} 로만 만든다.
 *
 * <p>확인을 요청 시점에 한다. 전에는 {@code @ConditionalOnProperty} 로 컨트롤러 자체를 껐지만, 한
 * 프로세스가 두 realm 을 담당하면 빈을 realm 별로 껐다 켤 수 없다. 다른 realm 에서는 404 다 —
 * 그 realm 에 이 경로는 없는 것이 맞다.
 *
 * <p>토큰은 응답 본문으로 내린다({@link IssuedTokens}).
 *
 * <p>provider 검증은 아웃바운드 어댑터가 수행한다(미설정 시 소셜 미지원).
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}/login/social")
@RequiredArgsConstructor
public class AuthSocialLoginApiController {

    private final AuthenticateWithSocialUseCase authenticateWithSocial;
    private final AuthenticationRealm authenticationRealm;

    @PostMapping
    public LoginResponse login(@PathVariable String realm, @Valid @RequestBody LoginRequest request) {
        Realm resolved = authenticationRealm.requireRealm(realm, Realm.CUSTOMER);

        AuthenticationResult result = authenticateWithSocial.authenticate(new SocialAuthCommand(
                SubjectRealm.subjectTypeOf(resolved), request.provider(), request.authorizationCode()));

        return new LoginResponse(result.subjectId(), result.subjectType().name(),
                IssuedTokens.of(result.tokens()));
    }

    public record LoginRequest(@NotNull SocialProvider provider, @NotBlank String authorizationCode) {
    }

    public record LoginResponse(String subjectId, String subjectType, IssuedTokens tokens) {
    }
}
