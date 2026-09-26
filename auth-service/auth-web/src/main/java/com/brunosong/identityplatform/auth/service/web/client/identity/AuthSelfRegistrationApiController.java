package com.brunosong.identityplatform.auth.service.web.client.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithEmailUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithEmailCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.shared.RegistrationMethod;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 셀프 가입 API — auth 가 신원 권위자로서 Principal 과 자격증명을 만든다.
 * 프로필 생성은 등록 이벤트를 받은 쪽이 한다. 토큰은 발급하지 않는다(가입 후 로그인은 별도).
 *
 * <h2>가입 방식이 둘이다</h2>
 * <pre>
 * POST /register                      비밀번호를 정해 가입
 * POST /register/email/send-code      가입용 인증번호 발송
 * POST /register/email                인증번호로 가입 (비밀번호 없음)
 * </pre>
 *
 * 어느 방식이 어느 realm 에 열리는지는 <b>컨트롤러가 아니라 {@link Realm} 이 정한다.</b>
 * 포털은 둘 다, 어드민은 이메일만 열려 있다. 직원은 비밀번호 계정을 갖지 않으므로 어드민에
 * 비밀번호 가입을 열면 이 서비스가 만들 수 없는 계정을 요구하는 경로가 생긴다.
 *
 * <p>자기 realm 에 열리지 않은 방식은 <b>404</b> 다 — 403 이 아닌 이유는 "여기에도 그 API 가 있긴
 * 한데 막혀 있다" 를 알려줄 이유가 없기 때문이다. 그 판단은
 * {@link AuthenticationRealm#requireSelfRegistration(String, RegistrationMethod)} 한 곳에서 한다.
 *
 * <h2>가입 경로는 realm 위에 있다</h2>
 * 전에는 {@code /api/auth/customer/register} 하나뿐이었고, 만드는 신원의 realm 이 경로에 박혀 있었다.
 * 그러면 <b>경로 이름이 정책</b>이 된다 — realm 이 하나 늘 때마다 경로를 새로 만들어야 하고,
 * 어느 realm 이 셀프 가입을 여는지는 컨트롤러를 열어봐야 안다. 지금은 경로가 realm 을 받고,
 * 정책은 realm 이 들고 있다. Keycloak 도 realm 설정의 "User registration" 토글로 이 경로를 연다.
 *
 * <p>만들 신원의 realm 도 요청 본문이 아니라 경로가 정한다. 본문으로 받으면 포털에
 * 가입하면서 직원 신원을 만들어 달라고 적을 수 있다.
 *
 * <h2>인증번호 발송은 언제나 202 다</h2>
 * 이미 가입된 주소인지 여부가 응답에 드러나면, 주소를 넣어보는 것만으로 누가 가입돼 있는지
 * 훑을 수 있다(계정 열거). 그래서 발송 여부와 무관하게 같은 응답을 준다 — 실제로 등록된
 * 주소에는 가입용 코드가 나가지 않는다.
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}")
@RequiredArgsConstructor
public class AuthSelfRegistrationApiController {

    private final RegisterWithPasswordUseCase registerWithPassword;
    private final RequestRegistrationOtpUseCase requestRegistrationOtp;
    private final RegisterWithEmailUseCase registerWithEmail;
    private final AuthenticationRealm authenticationRealm;

    /**
     * 비밀번호로 가입한다. 이 realm 에서는 별도 아이디 없이 이메일이 로그인 식별자다.
     *
     * <p><b>이메일 소유는 확인하지 않는다.</b> 남의 주소로도 가입할 수 있다는 뜻이고,
     * 그것이 아래 이메일 가입과의 가장 큰 차이다.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@PathVariable String realm,
                                     @Valid @RequestBody SelfRegisterRequest request) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.PASSWORD);

        String principalId = registerWithPassword.register(new RegisterWithPasswordCommand(
                resolved, request.email(), request.name(), request.phoneNumber(),
                request.email(), request.password())).principalId().value();
        return new RegisterResponse(principalId);
    }

    /** 가입용 인증번호 발송. 이미 등록된 주소에는 보내지 않지만 응답은 같다. */
    @PostMapping("/register/email/send-code")
    public ResponseEntity<Void> sendRegistrationCode(@PathVariable String realm,
                                                     @Valid @RequestBody SendCodeRequest request) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);
        requestRegistrationOtp.request(resolved, request.email());
        return ResponseEntity.accepted().build();
    }

    /**
     * 인증번호로 가입한다. 비밀번호를 받지 않는다.
     *
     * <p>인증번호를 받아낸 것이 곧 그 주소의 주인이라는 증거라, <b>가입 시점에 이메일 소유가
     * 확인된다.</b> 대신 이 사람은 이메일 OTP 로만 로그인한다 — 정한 비밀번호가 없다.
     */
    @PostMapping("/register/email")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse registerWithEmail(@PathVariable String realm,
                                              @Valid @RequestBody EmailRegisterRequest request) {
        Realm resolved = authenticationRealm.requireSelfRegistration(realm, RegistrationMethod.EMAIL_OTP);

        String principalId = registerWithEmail.register(new RegisterWithEmailCommand(
                resolved, request.email(), request.name(), request.phoneNumber(),
                request.verificationCode())).principalId().value();
        return new RegisterResponse(principalId);
    }

    public record SelfRegisterRequest(
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Email String email,
            @NotBlank String name,
            String phoneNumber) {
    }

    public record SendCodeRequest(@NotBlank @Email String email) {
    }

    public record EmailRegisterRequest(
            @NotBlank @Email String email,
            @NotBlank String name,
            String phoneNumber,
            @NotBlank String verificationCode) {
    }

    public record RegisterResponse(String principalId) {
    }
}
