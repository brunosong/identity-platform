package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EstablishRegisteredAuthenticationUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입 직후의 로그인 확정. 다른 로그인 경로와 같은 {@link AuthenticationCompletion} 을 밟는다. */
@Service
@RequiredArgsConstructor
public class RegisteredAuthenticationService implements EstablishRegisteredAuthenticationUseCase {

    private final PrincipalRepository principalRepository;
    private final AuthenticationCompletion authenticationCompletion;

    @Override
    @Transactional
    public AuthenticatedSubject afterRegistration(AuthenticatedSubject registered) {
        Principal principal = principalRepository.findById(registered.principalId())
                .orElseThrow(() -> new IllegalStateException("방금 가입한 신원이 없습니다: " + registered.principalId()));
        Principal established = authenticationCompletion.establish(principal);
        return new AuthenticatedSubject(established.getPrincipalId(),
                established.getSubjectId().value(), established.getRealm());
    }
}
