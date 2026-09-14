package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestRegistrationOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>가입용</b> 이메일 OTP 발송 요청.
 *
 * <p>{@link RequestEmailOtpService} 와 조건이 정반대다 — 그쪽은 등록된 주소에만, 여기는 등록되지
 * <b>않은</b> 주소에만 보낸다. 이미 가입한 사람에게 가입 코드를 보낼 이유가 없기 때문이다.
 *
 * <p><b>이 한 줄이 "이미 가입된 이메일입니다" 응답을 없앤다.</b> 그런 응답을 주면 주소를 넣어보는
 * 것만으로 누가 가입돼 있는지 훑을 수 있다(계정 열거). 여기서는 등록된 주소에 코드가 나가지
 * 않으므로, 그 주소로는 가입 검증 단계에 도달할 수조차 없다 — 틀린 코드와 같은 실패로 끝난다.
 * 컨트롤러가 어느 쪽이든 202 를 돌려주는 것과 한 쌍이다.
 *
 * <p>realm 을 함께 받는다. 이메일은 유형 안에서만 유일하므로, 같은 주소라도 고객으로는
 * 등록돼 있고 직원으로는 안 돼 있을 수 있다 — 그 경우 직원 가입 코드는 정상적으로 나간다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestRegistrationOtpService implements RequestRegistrationOtpUseCase {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailOtpIssuer otpIssuer;

    @Override
    @Transactional
    public void request(Realm realm, String email) {
        if (emailAccountRepository.findByEmail(realm, email).isPresent()) {
            log.info("이미 등록된 이메일 가입 OTP 요청 — 발송 생략(열거 방지): email={}", email);
            return;
        }
        otpIssuer.issue(email);
    }
}
