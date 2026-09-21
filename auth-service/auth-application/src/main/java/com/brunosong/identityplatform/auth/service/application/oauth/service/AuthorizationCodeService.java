package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.IssueAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.IssueAuthorizationCodeCommand;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.out.AuthorizationCodeRepository;
import com.brunosong.identityplatform.auth.service.domain.oauth.AuthorizationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 인가 코드에 관한 유스케이스 구현.
 *
 * <p>등록된 앱을 다루는 {@code OAuthClientService} 와 나눠 둔다. 저쪽은 오래 사는 등록 정보이고
 * 이쪽은 1분 사는 발급물이라 같은 개념이 아니다.
 *
 * <p>지금은 발급뿐이다. 코드를 토큰으로 바꾸는 쪽(교환)은 {@code /token} 을 만들 때 여기 붙는다 -
 * 같은 개념을 다루므로 클래스가 늘지 않고 메서드가 는다.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationCodeService implements IssueAuthorizationCodeUseCase {

    private final AuthorizationCodeRepository codeRepository;

    /**
     * 코드를 만들어 그 자리에서 저장한다.
     *
     * <p>수명을 재는 시각을 도메인이 정하지 않고 여기서 넘긴다. 도메인이 {@code Instant.now()} 를
     * 직접 부르면 "1분 뒤에 죽는다" 를 시계를 돌리지 않고는 확인할 수 없다.
     */
    @Override
    @Transactional
    public AuthorizationCode issue(IssueAuthorizationCodeCommand command) {
        AuthorizationCode code = AuthorizationCode.issue(
                command.realm(), command.request(), command.principalId(), Instant.now());

        codeRepository.save(code);
        return code;
    }
}
