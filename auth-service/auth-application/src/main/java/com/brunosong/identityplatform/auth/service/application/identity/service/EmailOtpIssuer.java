package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailOtpStore;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.OtpEmailSenderPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PasswordEncoderPort;
import com.brunosong.identityplatform.auth.service.domain.identity.EmailOtpChallenge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 인증번호를 만들어 저장하고 보낸다. <b>로그인과 가입이 이것을 함께 쓴다.</b>
 *
 * <p>따로 두면 TTL·쿨다운·로컬 고정코드가 두 벌이 되고, 한쪽만 고쳐지는 날이 온다 —
 * 특히 쿨다운은 한쪽이 느슨하면 그쪽으로 메일 폭탄이 나간다.
 *
 * <p><b>"누구에게 보낼지" 는 여기서 정하지 않는다.</b> 로그인은 등록된 이메일에만 보내고, 가입은
 * 등록되지 <i>않은</i> 이메일에만 보낸다 — 정반대 조건이라 부르는 쪽이 판단한다. 여기 오면
 * 보낸다는 뜻이다.
 *
 * <p>챌린지에 용도(로그인/가입) 표시는 없다. 두 조건이 서로 배타적이라 한 이메일이 동시에 양쪽
 * 대상이 될 수 없고, 그래서 한 코드가 두 용도로 쓰일 상황이 생기지 않는다. 용도가 더 늘어나면
 * 그때는 챌린지에 구분을 넣어야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailOtpIssuer {

    /** 동일 이메일 재발송 최소 간격(초) — 메일 폭탄/비용 남용 방지. */
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    /** OTP 만료(분). */
    private static final long OTP_TTL_MINUTES = 5;
    /** 로컬 개발 환경 고정 인증번호 — 메일 발송 없이 이 코드로 진행. */
    private static final String LOCAL_FIXED_CODE = "123456";

    private final EmailOtpStore otpStore;
    private final PasswordEncoderPort passwordEncoder;
    private final Environment environment;
    private final ObjectProvider<OtpEmailSenderPort> emailSenderProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 쿨다운에 걸리면 조용히 넘어간다. 부르는 쪽은 보냈는지 여부를 알 수 없고, 알 필요도 없다. */
    public void issue(String email) {
        Optional<Instant> lastCreated = otpStore.findLatestCreatedAt(email);
        if (lastCreated.isPresent()
                && lastCreated.get().isAfter(Instant.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            log.info("OTP 재발송 쿨다운 — 발송 생략: email={}", email);
            return;
        }

        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        String rawCode = local ? LOCAL_FIXED_CODE : String.format("%06d", secureRandom.nextInt(1_000_000));

        Instant now = Instant.now();
        // 코드 원문은 저장하지 않는다. DB 가 새도 유효한 인증번호가 함께 새지는 않는다.
        otpStore.save(EmailOtpChallenge.issue(
                email, passwordEncoder.encode(rawCode), now, now.plus(Duration.ofMinutes(OTP_TTL_MINUTES))));

        if (local) {
            log.info("[local] OTP 메일 발송 생략 — 고정코드({}) 사용: email={}", LOCAL_FIXED_CODE, email);
            return;
        }

        OtpEmailSenderPort sender = emailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("메일 발송 어댑터가 설정되지 않았습니다(OtpEmailSenderPort).");
        }
        sender.send(email, rawCode);
    }
}
