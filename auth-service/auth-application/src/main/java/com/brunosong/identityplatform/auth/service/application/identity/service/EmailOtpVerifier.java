package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 인증번호를 검증하고 소모한다. <b>로그인과 가입이 이것을 함께 쓴다.</b>
 *
 * <p>따로 두면 시도 횟수 제한이 두 벌이 되고, 한쪽이 빠지면 그쪽이 무제한 대입 경로가 된다.
 * 여섯 자리 숫자는 100만 가지라 제한이 없으면 금방 뚫린다.
 *
 * <p>성공하면 챌린지를 사용 처리한다 — 한 번 쓴 코드는 다시 쓰이지 않는다.
 */
@Component
@RequiredArgsConstructor
public class EmailOtpVerifier {

    /** 연속 실패 허용 횟수(초과 시 해당 챌린지 거부). */
    private static final int MAX_ATTEMPTS = 5;

    private final EmailOtpStore otpStore;
    private final PasswordEncoderPort passwordEncoder;

    /**
     * 맞으면 소모하고 끝난다. 틀리면 예외다.
     *
     * <p>실패 기록은 {@link EmailOtpStore#recordFailedAttempt} 로 <b>따로</b> 남긴다. 여기서 던지는
     * 예외가 부르는 쪽의 트랜잭션을 되돌리는데, 시도 횟수를 같은 트랜잭션에서 올리면 그 증가분도
     * 함께 사라져 잠금이 영원히 걸리지 않는다.
     */
    public void verify(String email, String code) {
        EmailOtpChallenge otp = otpStore.findLatestUnused(email)
                .orElseThrow(() -> new AuthenticationFailedException("인증번호가 유효하지 않습니다. 다시 요청해주세요."));

        if (otp.isBlocked(MAX_ATTEMPTS)) {
            throw new AuthenticationFailedException("인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해주세요.");
        }
        if (otp.isExpired(Instant.now())) {
            throw new AuthenticationFailedException("인증번호가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.recordAttempt();
            otpStore.recordFailedAttempt(otp);
            throw new AuthenticationFailedException("인증번호가 일치하지 않습니다.");
        }

        otp.markUsed();
        otpStore.save(otp);
    }
}
