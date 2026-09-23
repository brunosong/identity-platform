package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RequestEmailOtpUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.EmailAccountRepository;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>로그인용</b> 이메일 OTP 발송 요청.
 *
 * <p>이 서비스가 하는 판단은 하나뿐이다 — "그 유형으로 등록된 이메일인가". 등록된 경우에만
 * 보낸다. 코드 생성·저장·발송과 쿨다운은 {@link EmailOtpIssuer} 가 갖는다(가입용과 공유).
 *
 * <p>미등록이면 <b>조용히</b> 끝난다. 없는 이메일에 다른 응답을 주면 그 응답만으로 누가 가입돼
 * 있는지 훑어낼 수 있다(계정 열거). 컨트롤러가 어느 쪽이든 202 를 돌려주는 것과 한 쌍이다.
 *
 * <p>가입용은 조건이 정반대다 — {@link RequestRegistrationOtpService} 참고.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestEmailOtpService implements RequestEmailOtpUseCase {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailOtpIssuer otpIssuer;

    @Override
    @Transactional
    public void request(Realm realm, String email) {
        if (emailAccountRepository.findByEmail(realm, email).isEmpty()) {
            // realm 을 함께 적는다. 같은 주소가 다른 realm 에 있는 경우가 흔한데, realm 이 없으면
            // 로그만 보고는 "없는 주소" 와 "다른 realm 의 주소" 를 구분할 수 없다.
            log.info("미등록 이메일 로그인 OTP 요청 — 발송 생략(열거 방지): realm={}, email={}", realm, email);
            return;
        }
        otpIssuer.issue(email);
    }
}
