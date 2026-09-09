package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.SubjectRealm;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 셀프 가입 API — auth 가 신원 권위자로서 Principal 과 로컬 자격증명을 만든다.
 * 프로필 생성은 등록 이벤트를 받은 쪽이 한다. 토큰은 발급하지 않는다(가입 후 로그인은 별도).
 *
 * <h2>가입 경로는 하나다</h2>
 * 전에는 {@code /api/auth/customer/register} 하나뿐이었고, 만드는 주체 유형이 경로에 박혀 있었다.
 * 그러면 <b>경로 이름이 정책</b>이 된다 — realm 이 하나 늘 때마다 경로를 새로 만들어야 하고,
 * 어느 realm 이 셀프 가입을 여는지는 컨트롤러를 열어봐야 안다.
 *
 * <p>지금은 경로가 realm 을 받고({@code /api/auth/realms/{realm}/register}), 그 realm 이 셀프 가입을
 * 여는지를 {@link Realm#allowsSelfRegistration()} 이 정한다. 닫힌 realm 에서는 404 다 — 어드민에서
 * 가입이 열리면 아무나 자기 자신을 직원으로 만든다. 로그인·로그아웃·재발급과 같은 모양이 되고,
 * Keycloak 도 realm 설정의 "User registration" 토글로 이 경로를 열고 닫는다.
 *
 * <p>만들 주체 유형은 요청이 아니라 realm 이 정한다({@link SubjectRealm}). 본문으로 받으면 포털에
 * 가입하면서 직원 신원을 만들어 달라고 적을 수 있다.
 *
 * <p>입력 검증을 요청 모델에 붙인다. 예전에는 검증이 전혀 없어 빈 비밀번호나 형식이 아닌 이메일이
 * 그대로 저장까지 내려갔다 — 그 이메일은 이후 로그인 식별자로 쓰이므로 여기서 걸러야 한다.
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}")
@RequiredArgsConstructor
public class AuthSelfRegistrationApiController {

    private final RegisterWithPasswordUseCase registerWithPassword;
    private final AuthenticationRealm authenticationRealm;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@PathVariable String realm,
                                     @Valid @RequestBody SelfRegisterRequest request) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm);
        SubjectType subjectType = SubjectRealm.subjectTypeOf(resolved);

        // 이 realm 에서는 별도 아이디 없이 이메일이 로그인 식별자다.
        String principalId = registerWithPassword.register(new RegisterWithPasswordCommand(
                subjectType, request.email(), request.name(), request.phoneNumber(),
                request.email(), request.password()));
        return new RegisterResponse(principalId);
    }

    public record SelfRegisterRequest(
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Email String email,
            @NotBlank String name,
            String phoneNumber) {
    }

    public record RegisterResponse(String principalId) {
    }
}
