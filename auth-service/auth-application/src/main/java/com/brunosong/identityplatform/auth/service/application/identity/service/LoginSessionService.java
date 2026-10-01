package com.brunosong.identityplatform.auth.service.application.identity.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.EndLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.FindLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.StartLoginSessionUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticatedSubject;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.LoginSessionRepository;
import com.brunosong.identityplatform.auth.service.application.identity.ports.out.PrincipalRepository;
import com.brunosong.identityplatform.auth.service.domain.identity.LoginSession;
import com.brunosong.identityplatform.auth.service.domain.identity.Principal;
import com.brunosong.identityplatform.auth.service.domain.identity.valueobject.PrincipalId;
import com.brunosong.identityplatform.auth.service.domain.shared.Realm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 로그인 세션에 관한 유스케이스 구현.
 *
 * <p>브라우저가 로그인 상태인지를 다루는 곳이다. 앱이 들고 다니는 토큰과는 다른 물건이라
 * 토큰 발급과 섞이지 않게 클래스를 따로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginSessionService implements StartLoginSessionUseCase, FindLoginSessionUseCase,
        EndLoginSessionUseCase {

    private final LoginSessionRepository sessionRepository;
    private final PrincipalRepository principalRepository;

    @Override
    @Transactional
    public LoginSession start(Realm realm, PrincipalId principalId) {
        LoginSession session = LoginSession.start(realm, principalId, Instant.now());

        sessionRepository.save(session);
        return session;
    }

    /**
     * 세션을 끊는다. 없는 세션이어도 조용히 지나간다.
     *
     * <p>끝난 상태를 만드는 것이 목적이라, 이미 없으면 목적이 이미 이뤄진 것이다. 만료된 쿠키를
     * 들고 온 사람도 로그아웃할 수 있어야 한다.
     */
    @Override
    @Transactional
    public void end(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionRepository.delete(sessionId);
    }

    /**
     * 살아 있는 세션이면 그 사람을 돌려준다. 통합 로그인이 성립하는 자리다.
     *
     * <p>세션에 적힌 realm 을 경로의 realm 과 다시 대조한다. 쿠키 경로가 이미
     * {@code /realms/{realm}} 로 갈려 있어 브라우저가 알아서 나눠 보내지만, 쿠키는 결국
     * 요청자가 실어 보내는 값이다. 고객 세션이 어드민 요청에 실려 오면 여기서 끝난다.
     *
     * <p>신원을 한 번 더 읽는다. 화면에 쓸 {@code subjectId} 가 거기 있고, 세션이 살아 있는
     * 동안 계정이 사라졌다면 그것도 여기서 걸린다.
     */
    @Override
    public Optional<AuthenticatedSubject> findActive(Realm realm, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        Optional<LoginSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        // 다른 realm 의 세션이거나 끝난 세션이면 로그인하지 않은 것과 같다.
        LoginSession session = found.get();
        if (session.getRealm() != realm || session.isExpired(Instant.now())) {
            return Optional.empty();
        }

        // 세션이 살아 있는 동안 신원이 지워졌다면 여기서 걸린다.
        Optional<Principal> principal = principalRepository.findById(session.getPrincipalId());
        if (principal.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedSubject(principal.get().getPrincipalId(),
                principal.get().getSubjectId().value(), principal.get().getRealm()));
    }
}
