package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 비밀번호 자격증명 확인 — 해시 비교와 연속 실패 잠금을 함께 다룬다.
 *
 * <p>실패 기록은 인증 트랜잭션과 무관하게 커밋된다. 실패로 트랜잭션이 되돌아갈 때
 * 실패 횟수까지 사라지면 잠금이 영원히 걸리지 않는다.
 *
 * <p>조회는 realm 으로 좁힌다. 상대 realm 에 같은 loginId 가 있어도 걸리지 않고, 없는 아이디와
 * 같은 실패로 끝난다(계정 열거 방지 겸 realm 격리).
 */
@Component
@RequiredArgsConstructor
class PasswordCredentialVerifier {

    private static final String GENERIC_FAIL = "아이디 또는 비밀번호가 올바르지 않습니다.";
    private static final String LOCKED_FAIL = "로그인 시도가 많아 계정이 일시적으로 잠겼습니다. 잠시 후 다시 시도해주세요.";
    /** 연속 실패 허용 횟수(초과 시 잠금). 이메일 OTP 챌린지 잠금과 대칭. */
    private static final int MAX_ATTEMPTS = 5;
    /** 임계치 도달 시 잠금 유지 시간. */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final PasswordAccountRepository passwordAccountRepository;
    private final PasswordEncoderPort passwordEncoder;

    public PasswordAccount verify(Realm realm, String loginId, String password)
            throws AuthenticationFailedException {
        PasswordAccount account = passwordAccountRepository.findByLoginId(realm, loginId)
                .orElseThrow(() -> new AuthenticationFailedException(GENERIC_FAIL));

        if (account.isLocked(Instant.now())) {
            // 잠긴 동안은 비밀번호를 확인하지 않는다(대입 자체 차단). 잠금 사실은 사용자에게 안내한다.
            throw new AuthenticationFailedException(LOCKED_FAIL);
        }

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            account.recordFailure(Instant.now(), MAX_ATTEMPTS, LOCK_DURATION);
            // 실패 기록은 인증 트랜잭션 롤백과 무관하게 남아야 한다(REQUIRES_NEW).
            passwordAccountRepository.updateLoginState(account);
            throw new AuthenticationFailedException(GENERIC_FAIL);
        }

        // 성공: 남아 있던 실패 카운트/잠금을 해제.
        if (account.hasFailureState()) {
            account.resetFailureState();
            passwordAccountRepository.updateLoginState(account);
        }
        return account;
    }

}
