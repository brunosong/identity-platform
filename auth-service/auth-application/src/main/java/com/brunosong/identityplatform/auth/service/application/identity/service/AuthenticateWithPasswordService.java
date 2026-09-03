package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.AuthenticateWithPasswordUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디/비밀번호 로그인.
 *
 * <p>흐름: loginId 로 자격증명 확인(잠금 포함) → 연결된 Principal 조회 → 인증 성립 처리.
 * 아이디 미존재와 비밀번호 불일치는 같은 메시지로 실패한다(계정 열거 방지).
 */
@Service
@RequiredArgsConstructor
public class AuthenticateWithPasswordService implements AuthenticateWithPasswordUseCase {

    private final PrincipalRepository principalRepository;
    private final PasswordCredentialVerifier passwordCredentialVerifier;
    private final AuthenticationCompletion authenticationCompletion;

    @Override
    @Transactional
    public AuthenticationResult authenticate(PasswordAuthCommand command) {
        PasswordAccount account = passwordCredentialVerifier.verify(command.loginId(), command.password());

        Principal principal = principalRepository.findById(account.getPrincipalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Principal not found for passwordAccount=" + account.getPasswordAccountId()));

        return authenticationCompletion.complete(principal);
    }
}
