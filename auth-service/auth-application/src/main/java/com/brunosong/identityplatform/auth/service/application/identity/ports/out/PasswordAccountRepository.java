package com.brunosong.identityplatform.auth.service.application.identity.ports.out;

import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;

import java.util.Optional;

/**
 * 비밀번호 자격증명(로그인ID/비번) 저장 드리븐 포트.
 */
public interface PasswordAccountRepository {

    Optional<PasswordAccount> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    PasswordAccount save(PasswordAccount account);

    /**
     * 로그인 실패/성공에 따른 잠금 상태(failed_attempts, locked_until)만 갱신한다.
     * 실패 기록은 인증 트랜잭션이 롤백되어도 남아야 하므로 어댑터가 독립 트랜잭션(REQUIRES_NEW)으로 커밋한다.
     */
    void updateLoginState(PasswordAccount account);
}
