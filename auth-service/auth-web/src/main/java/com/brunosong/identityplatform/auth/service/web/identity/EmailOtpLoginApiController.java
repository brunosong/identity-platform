package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.web.support.AuthCookies;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
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
 * 이메일 OTP 로그인 API — 인증번호 발송과 검증+토큰발급을 소유한다.
 *
 * <p>주체 유형은 호스트 설정({@link AuthenticationRealm})에서 온다. 예전에는 URL(/api/auth/employee/login)과
 * 컨트롤러 상수가 EMPLOYEE 로 못박고 있었는데, 이 jar 는 고객 호스트에도 그대로 실린다 — 고객 호스트에
 * 직원 로그인 URL 이 떠 있었고, 직원이 그 경로로 로그인하면 발급기는 자기 설정대로 고객 realm 토큰을 찍었다.
 * 인증한 주체와 발급된 토큰의 realm 이 어긋난 것이다. 이제 둘 다 같은 프로퍼티를 본다.
 *
 * <p>그래서 이 API 는 realm 중립이다 — 직원 호스트에선 직원 OTP 로그인, 고객 호스트에선 고객 OTP 로그인이다.
 * 상대 realm 의 이메일은 {@code findByEmail(subjectType, email)} 에서 걸리지 않아 인증되지 않는다.
 * 엔드포인트를 가르는 기준은 "누가"(직원/고객)가 아니라 "무엇으로"(비밀번호/OTP/소셜) 로그인하느냐다.
 *
 * <p>토큰은 {@link AuthCookies} 규칙으로 쿠키에 실린다 — 비밀번호 로그인과 같은 규칙이다.
 * 예전에는 컨트롤러마다 쿠키를 따로 구워서 만료가 서로 달랐다.
 *
 * <p>실패는 HTTP 상태코드로 알린다. 예전에는 인증 실패도 처리 오류도 200 + {@code success:false} 로
 * 나갔다 — 호출자와 모니터링 어느 쪽도 실패를 구분할 수 없었다.
 */
@RestController
@RequestMapping("/api/auth/login/email-otp")
@RequiredArgsConstructor
public class EmailOtpLoginApiController {

    private final RequestEmailOtpUseCase requestEmailOtp;
    private final AuthenticateWithEmailOtpUseCase authenticateOtp;
    private final ListSubjectPermissionsUseCase subjectPermissions;
    private final AuthCookies authCookies;
    private final AuthenticationRealm authenticationRealm;

    /** 인증번호 발송. 계정 열거 방지를 위해 가입 여부와 무관하게 같은 응답을 준다. */
    @PostMapping("/send-code")
    public ResponseEntity<Void> sendVerificationCode(@Valid @RequestBody SendCodeRequest request) {
        requestEmailOtp.request(authenticationRealm.subjectType(), request.email());
        return ResponseEntity.accepted().build();
    }

    /** 인증번호 검증 → 토큰 발급 → 쿠키 세팅. */
    @PostMapping
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticateOtp.authenticate(new EmailOtpAuthCommand(
                authenticationRealm.subjectType(), request.email(), request.verificationCode()));
        authCookies.write(response, result.tokens());

        // 프론트 메뉴/버튼 렌더용 권한 목록. /api/auth/my-permissions 로도 조회된다.
        List<String> permissions = subjectPermissions.of(authenticationRealm.realm(), result.subjectId());
        return new LoginResponse(result.subjectId(), permissions);
    }

    public record SendCodeRequest(@NotBlank @Email String email) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String verificationCode) {
    }

    public record LoginResponse(String subjectId, List<String> permissions) {
    }
}
