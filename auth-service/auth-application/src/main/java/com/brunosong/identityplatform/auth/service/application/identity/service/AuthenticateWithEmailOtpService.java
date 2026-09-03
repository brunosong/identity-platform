package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.EmailOtpAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 이메일 OTP 로그인. auth 가 OTP 검증을 직접 소유한다 — 코드 검증은 auth 의 {@link EmailOtpStore},
 * 주체 해석은 {@link EmailAccountRepository#findByEmail}(이메일 → principalId → Principal)로 한다.
 * 토큰 형식만 호스트({@link TokenIssuerPort})가 정한다. 실패는 계정 열거 방지를 위해 동일 예외로 던진다.
 */
@Service
@RequiredArgsConstructor
public class AuthenticateWithEmailOtpService implements AuthenticateWithEmailOtpUseCase {

    /** 연속 실패 허용 횟수(초과 시 해당 챌린지 거부). */
    private static final int MAX_ATTEMPTS = 5;

    private final EmailAccountRepository emailAccountRepository;
    private final PrincipalRepository principalRepository;
    private final EmailOtpStore otpStore;
    private final PasswordEncoderPort passwordEncoder;
    private final AuthenticationCompletion authenticationCompletion;

    @Override
    @Transactional
    public AuthenticationResult authenticate(EmailOtpAuthCommand command) {
        String email = command.email();

        EmailAccount emailAccount = emailAccountRepository.findByEmail(command.subjectType(), email)
                .orElseThrow(() -> new AuthenticationFailedException("인증에 실패했습니다."));
        Principal principal = principalRepository.findById(emailAccount.getPrincipalId())
                .orElseThrow(() -> new AuthenticationFailedException("인증에 실패했습니다."));

        EmailOtpChallenge otp = otpStore.findLatestUnused(email)
                .orElseThrow(() -> new AuthenticationFailedException("인증번호가 유효하지 않습니다. 다시 요청해주세요."));

        if (otp.isBlocked(MAX_ATTEMPTS)) {
            throw new AuthenticationFailedException("인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해주세요.");
        }
        if (otp.isExpired(Instant.now())) {
            throw new AuthenticationFailedException("인증번호가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!passwordEncoder.matches(command.otpCode(), otp.getCodeHash())) {
            otp.recordAttempt();
            // 이 예외가 인증 트랜잭션을 되돌리므로 시도 횟수는 별도 트랜잭션으로 남긴다.
            otpStore.recordFailedAttempt(otp);
            throw new AuthenticationFailedException("인증번호가 일치하지 않습니다.");
        }

        otp.markUsed();
        otpStore.save(otp);

        return authenticationCompletion.complete(principal);
    }
}
