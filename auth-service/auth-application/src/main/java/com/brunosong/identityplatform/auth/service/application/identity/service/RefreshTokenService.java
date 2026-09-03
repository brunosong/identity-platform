package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 재발급. 토큰 검증은 키를 쥔 발급기({@link TokenIssuerPort})가, 주체 재로딩은 auth 가 한다.
 * 재발급은 {@code issue} 를 재사용하므로 권한/리비전이 최신으로 다시 실린다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {

    private final PrincipalRepository principalRepository;
    private final TokenIssuance tokenIssuance;

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResult refresh(String refreshToken) {
        String subjectId = tokenIssuance.subjectIdFromRefreshToken(refreshToken);
        Principal principal = principalRepository.findBySubjectId(new SubjectId(subjectId))
                .orElseThrow(() -> new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다."));

        // 재발급은 발급과 같은 경로를 쓴다 — 권한과 리비전이 그 시점 값으로 다시 실린다.
        return tokenIssuance.resultFor(principal);
    }
}
