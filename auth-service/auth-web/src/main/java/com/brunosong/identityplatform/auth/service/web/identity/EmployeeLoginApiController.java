package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 직원(EMPLOYEE) 이메일 OTP 로그인 API.
 *
 * <p>인증번호 발송과 검증+토큰발급을 소유한다. 토큰은 {@link AuthCookies} 규칙으로 쿠키에 실린다 —
 * 비밀번호 로그인과 같은 규칙이다. 예전에는 컨트롤러마다 쿠키를 따로 구워서 만료가 서로 달랐다.
 *
 * <p>실패는 HTTP 상태코드로 알린다. 예전에는 인증 실패도 처리 오류도 200 + {@code success:false} 로
 * 나갔다 — 호출자와 모니터링 어느 쪽도 실패를 구분할 수 없었다.
 */
@RestController
@RequestMapping("/api/auth/employee")
@RequiredArgsConstructor
public class EmployeeLoginApiController {

    private static final Realm REALM = Realm.EMPLOYEE;

    private final RequestEmailOtpUseCase requestEmailOtp;
    private final AuthenticateWithEmailOtpUseCase authenticateOtp;
    private final ListSubjectPermissionsUseCase subjectPermissions;
    private final AuthCookies authCookies;

    /** 인증번호 발송. 계정 열거 방지를 위해 가입 여부와 무관하게 같은 응답을 준다. */
    @PostMapping("/send-verification-code")
    public ResponseEntity<Void> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        requestEmailOtp.request(SubjectType.EMPLOYEE, request.email());
        return ResponseEntity.accepted().build();
    }

    /** 인증번호 검증 → 토큰 발급 → 쿠키 세팅. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticateOtp.authenticate(
                new EmailOtpAuthCommand(SubjectType.EMPLOYEE, request.email(), request.verificationCode()));
        authCookies.write(response, result.tokens());

        // 프론트 메뉴/버튼 렌더용 권한 목록. /api/auth/my-permissions 로도 조회된다.
        List<String> permissions = subjectPermissions.of(REALM, result.subjectId());
        return new LoginResponse(result.subjectId(), permissions);
    }

    public record SendVerificationCodeRequest(@NotBlank @Email String email) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String verificationCode) {
    }

    public record LoginResponse(String subjectId, List<String> permissions) {
    }
}
