package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.EmailAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectType;

import java.util.Optional;

/**
 * 이메일 계정(이메일 OTP 로그인 식별자) 저장 드리븐 포트. 이메일로 주체(Principal)를 해석한다.
 *
 * <p>조회에 주체 유형을 함께 받는다. 이메일의 유일성이 유형 안에서만 성립하므로, 유형 없이 찾으면
 * 상대 realm 의 주체가 걸린다.
 */
public interface EmailAccountRepository {

    Optional<EmailAccount> findByEmail(SubjectType subjectType, String email);

    /** 신원으로 자기 이메일을 되찾는다. 토큰에 이메일을 실을 때 쓴다. */
    Optional<EmailAccount> findByPrincipalId(PrincipalId principalId);

    EmailAccount save(EmailAccount account);
}
