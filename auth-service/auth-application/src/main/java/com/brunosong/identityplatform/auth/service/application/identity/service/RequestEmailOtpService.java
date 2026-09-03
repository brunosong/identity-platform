package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.OtpEmailSenderPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 이메일 OTP 발송 요청. auth 가 코드 생성/저장을 직접 소유하고,
 * 등록 여부는 {@link EmailAccountRepository#findByEmail}(이메일 계정 존재) 로 판단한다.
 * 실제 전송은 {@link OtpEmailSenderPort}(호스트가 notification 이벤트로 구현)에 위임한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestEmailOtpService implements RequestEmailOtpUseCase {

    /** 동일 이메일 재발송 최소 간격(초) — 메일 폭탄/비용 남용 방지. */
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    /** OTP 만료(분). */
    private static final long OTP_TTL_MINUTES = 5;
    /** 로컬 개발 환경 고정 인증번호 — 메일 발송 없이 이 코드로 로그인. */
    private static final String LOCAL_FIXED_CODE = "123456";

    private final EmailAccountRepository emailAccountRepository;
    private final EmailOtpStore otpStore;
    private final PasswordEncoderPort passwordEncoder;
    private final Environment environment;
    private final ObjectProvider<OtpEmailSenderPort> emailSenderProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void request(SubjectType subjectType, String email) {
        // 계정 열거 방지: 그 유형으로 등록된 이메일 계정이 아니면 조용히 종료
        if (emailAccountRepository.findByEmail(subjectType, email).isEmpty()) {
            log.info("미등록 이메일 OTP 요청 — 발송 생략(열거 방지): email={}", email);
            return;
        }

        // 재발송 쿨다운 — 최근 발급 후 일정 시간 내 재요청은 조용히 무시
        Optional<Instant> lastCreated = otpStore.findLatestCreatedAt(email);
        if (lastCreated.isPresent()
                && lastCreated.get().isAfter(Instant.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            log.info("OTP 재발송 쿨다운 — 발송 생략: email={}", email);
            return;
        }

        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        String rawCode = local ? LOCAL_FIXED_CODE : String.format("%06d", secureRandom.nextInt(1_000_000));

        Instant now = Instant.now();
        otpStore.save(EmailOtpChallenge.issue(
                email, passwordEncoder.encode(rawCode), now, now.plus(Duration.ofMinutes(OTP_TTL_MINUTES))));

        if (local) {
            log.info("[local] OTP 메일 발송 생략 — 고정코드({}) 사용: email={}", LOCAL_FIXED_CODE, email);
            return;
        }

        OtpEmailSenderPort sender = emailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("이 호스트에는 OtpEmailSenderPort 가 없습니다(OTP 발송 미지원).");
        }
        sender.send(email, rawCode);
    }
}
