package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.RefreshTokenUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.TokenIssuerPort;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.dto.RefreshedSubject;
import com.brunosong.identityplatform.auth.service.domain.identity.AuthenticationFailedException;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.SubjectId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
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
    public AuthenticationResult refresh(Realm realm, String refreshToken) {
        RefreshedSubject refreshed = tokenIssuance.readRefreshToken(realm, refreshToken);
        // 요청한 realm 안에서 주체를 찾는다. 상대 realm 의 주체는 없는 것과 같다.
        Principal principal = principalRepository.findBySubjectId(realm, new SubjectId(refreshed.subjectId()))
                .orElseThrow(() -> new AuthenticationFailedException("리프레시 토큰이 유효하지 않습니다."));

        // 재발급은 발급과 같은 경로를 쓴다 — 권한과 리비전이 그 시점 값으로 다시 실린다.
        //
        // 클라이언트는 요청이 아니라 refresh 토큰이 정한다. 요청에서 받으면 refresh 토큰을 쥔 쪽이
        // audience 를 갈아끼울 수 있다 — 포털 토큰으로 어드민 서비스용 audience 를 받아내는 길이 열린다.
        return tokenIssuance.resultFor(realm, principal);
    }
}
