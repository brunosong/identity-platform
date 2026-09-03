package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RegisterWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.RegisterWithPasswordCommand;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고객 등록 API — auth 가 신원 권위자로서 Principal 과 로컬 자격증명을 만든다.
 * 프로필 생성은 등록 이벤트를 받은 쪽이 한다. 토큰은 발급하지 않는다(등록 후 로그인은 별도).
 *
 * <p>입력 검증을 요청 모델에 붙인다. 예전에는 검증이 전혀 없어 빈 비밀번호나 형식이 아닌 이메일이
 * 그대로 저장까지 내려갔다 — 그 이메일은 이후 로그인 식별자로 쓰이므로 여기서 걸러야 한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthCustomerRegistrationApiController {

    private final RegisterWithPasswordUseCase registerWithPassword;

    @PostMapping("/customer/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse registerCustomer(@Valid @RequestBody CustomerRegisterRequest request) {
        // 고객은 별도 아이디 없이 이메일이 로그인 식별자다.
        String principalId = registerWithPassword.register(new RegisterWithPasswordCommand(
                SubjectType.CUSTOMER, request.email(), request.name(), request.phoneNumber(),
                request.email(), request.password()));
        return new RegisterResponse(principalId);
    }

    public record CustomerRegisterRequest(
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Email String email,
            @NotBlank String name,
            String phoneNumber) {
    }

    public record RegisterResponse(String principalId) {
    }
}
