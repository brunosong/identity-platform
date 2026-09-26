package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishPasswordAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.command.PasswordAuthCommand;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.PasswordAccount;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디/비밀번호 로그인.
 *
 * <p>흐름: (유형, loginId) 로 자격증명 확인(잠금 포함) → 연결된 Principal 조회 → 인증 성립 처리.
 * 아이디 미존재와 비밀번호 불일치는 같은 메시지로 실패한다(계정 열거 방지).
 *
 * <p>자격증명 조회를 유형으로 좁히고, 거기 매달린 Principal 의 유형도 한 번 더 확인한다. 요청한 realm 과
 * 다른 realm 의 신원으로 토큰이 나가는 일은 자격증명이 맞아도 없어야 한다.
 *
 * <p>인증까지만 한다. 로그인 화면의 폼이 부르고, 토큰은 나중에 인가 코드를 바꾸러 온 앱에게 나간다.
 */
@Service
@RequiredArgsConstructor
public class AuthenticateWithPasswordService implements EstablishPasswordAuthenticationUseCase {

    private final PrincipalRepository principalRepository;
    private final PasswordCredentialVerifier passwordCredentialVerifier;
    private final AuthenticationCompletion authenticationCompletion;

    @Override
    @Transactional
    public AuthenticatedSubject withPassword(PasswordAuthCommand command) {
        Principal principal = authenticationCompletion.establish(authenticated(command));

        return new AuthenticatedSubject(principal.getPrincipalId(),
                principal.getSubjectId().value(), principal.getRealm());
    }

    /** 비밀번호를 확인하고 그 자격증명에 매달린 신원을 찾는다. */
    private Principal authenticated(PasswordAuthCommand command) {
        PasswordAccount account = passwordCredentialVerifier.verify(
                command.realm(), command.loginId(), command.password());

        Principal principal = principalRepository.findById(account.getPrincipalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Principal not found for passwordAccount=" + account.getPasswordAccountId()));

        // 자격증명과 신원의 유형이 어긋나면 데이터가 깨진 것이다. 토큰을 내주지 않는다.
        if (principal.getRealm() != command.realm()) {
            throw new AuthenticationFailedException("인증에 실패했습니다.");
        }

        return principal;
    }
}
