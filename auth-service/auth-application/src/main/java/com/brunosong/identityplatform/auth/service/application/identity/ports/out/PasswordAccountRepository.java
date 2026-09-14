package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;

import java.util.Optional;

/**
 * 비밀번호 자격증명(로그인ID/비번) 저장 드리븐 포트.
 *
 * <p>조회에 realm 을 함께 받는다 — {@link EmailAccountRepository} 와 같은 규칙이다.
 * loginId 의 유일성은 realm 안에서만 성립하므로, realm 없이 찾으면 상대 realm 의 자격증명이 걸린다.
 */
public interface PasswordAccountRepository {

    Optional<PasswordAccount> findByLoginId(Realm realm, String loginId);

    boolean existsByLoginId(Realm realm, String loginId);

    PasswordAccount save(PasswordAccount account);

    /**
     * 로그인 실패/성공에 따른 잠금 상태(failed_attempts, locked_until)만 갱신한다.
     * 실패 기록은 인증 트랜잭션이 롤백되어도 남아야 하므로 어댑터가 독립 트랜잭션(REQUIRES_NEW)으로 커밋한다.
     */
    void updateLoginState(PasswordAccount account);
}
