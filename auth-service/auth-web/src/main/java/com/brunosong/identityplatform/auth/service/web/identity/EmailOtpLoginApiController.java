package com.brunosong.identityplatform.auth.service.web.identity;

import com.brunosong.identityplatform.auth.service.application.authorization.ports.in.ListSubjectPermissionsUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.web.support.AuthenticationRealm;
import com.brunosong.identityplatform.auth.service.web.support.IssuedTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 이메일 OTP 로그인 API — 인증번호 발송과 검증+토큰발급을 소유한다.
 *
 * <p>realm 은 경로가 정한다. 이 API 는 realm 중립이다 — 직원 realm 이면 직원 OTP 로그인,
 * 고객 realm 이면 고객 OTP 로그인이다. 전에는 URL 에 {@code employee} 가 박혀 있어 고객은 쓸 수 없었고,
 * 그러면서도 고객 프로세스에 그 URL 이 떠 있어 직원이 고객 realm 토큰을 받을 수 있었다.
 *
 * <p>상대 realm 의 이메일은 {@code findByEmail(subjectType, email)} 에서 걸리지 않아 인증되지 않는다.
 * 엔드포인트를 가르는 기준은 "누가"(직원/고객)가 아니라 "무엇으로"(비밀번호/OTP/소셜) 로그인하느냐다.
 *
 * <p>실패는 HTTP 상태코드로 알린다. 예전에는 인증 실패도 처리 오류도 200 + {@code success:false} 로
 * 나갔다 — 호출자와 모니터링 어느 쪽도 실패를 구분할 수 없었다.
 */
@RestController
@RequestMapping("/api/auth/realms/{realm}/login/email-otp")
@RequiredArgsConstructor
public class EmailOtpLoginApiController {

    private final RequestEmailOtpUseCase requestEmailOtp;
    private final AuthenticateWithEmailOtpUseCase authenticateOtp;
    private final ListSubjectPermissionsUseCase subjectPermissions;
    private final AuthenticationRealm authenticationRealm;

    /** 인증번호 발송. 계정 열거 방지를 위해 가입 여부와 무관하게 같은 응답을 준다. */
    @PostMapping("/send-code")
    public ResponseEntity<Void> sendVerificationCode(@PathVariable String realm,
                                                     @Valid @RequestBody SendCodeRequest request) {
        requestEmailOtp.request(authenticationRealm.subjectTypeOf(realm), request.email());
        return ResponseEntity.accepted().build();
    }

    /** 인증번호 검증 → 토큰 발급. */
    @PostMapping
    public LoginResponse login(@PathVariable String realm, @Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authenticateOtp.authenticate(new EmailOtpAuthCommand(
                authenticationRealm.subjectTypeOf(realm), request.email(), request.verificationCode()));

        // 프론트 메뉴/버튼 렌더용 권한 목록. /api/auth/my-permissions 로도 조회된다.
        // 권한은 발급된 토큰과 같은 realm 에서 읽는다 — 경로가 아니라 인증된 주체를 따른다.
        List<String> permissions = subjectPermissions.of(
                authenticationRealm.of(realm), result.subjectId());

        return new LoginResponse(result.subjectId(), permissions, IssuedTokens.of(result.tokens()));
    }

    public record SendCodeRequest(@NotBlank @Email String email) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String verificationCode) {
    }

    public record LoginResponse(String subjectId, List<String> permissions, IssuedTokens tokens) {
    }
}
