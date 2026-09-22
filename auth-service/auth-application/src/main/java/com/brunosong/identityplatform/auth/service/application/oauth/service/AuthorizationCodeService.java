package com.brunosong.identityplatform.auth.service.application.oauth.service;

import com.brunosong.identityplatform.auth.service.application.identity.ports.in.IssueTokensForPrincipalUseCase;
import com.brunosong.identityplatform.auth.service.application.identity.ports.in.result.AuthenticationResult;
import com.brunosong.identityplatform.auth.service.application.oauth.exception.InvalidGrantException;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.ExchangeAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.IssueAuthorizationCodeUseCase;
import com.brunosong.identityplatform.auth.service.application.oauth.ports.in.command.ExchangeAuthorizationCodeCommand;
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
 * <p>발급과 교환이 여기 함께 있다. 같은 값을 다루는 앞뒤라 나눌 이유가 없다 - 발급할 때 무엇을
 * 적어 두는지와 교환할 때 무엇을 맞춰보는지가 한 화면에 보여야 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationCodeService implements IssueAuthorizationCodeUseCase,
        ExchangeAuthorizationCodeUseCase {

    private final AuthorizationCodeRepository codeRepository;
    private final IssueTokensForPrincipalUseCase issueTokens;

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

    /**
     * 코드를 토큰으로 바꾼다. 로그인 흐름의 마지막 걸음이다.
     *
     * <p><b>꺼내는 것이 곧 지우는 것이다.</b> 그래서 아래 검증에 걸린 코드도 되살아나지 않는다.
     * 명세도 그렇게 하라고 한다(RFC 6749 4.1.2) - 한 번 잘못 쓰인 코드는 태워버리는 편이 안전하다.
     * 같은 코드가 두 번 들어왔다는 것 자체가 어딘가 새고 있다는 신호이기도 하다.
     *
     * <p>실패는 전부 같은 예외다. 사유는 메시지로 남아 로그에만 찍힌다.
     */
    @Override
    @Transactional
    public AuthenticationResult exchange(ExchangeAuthorizationCodeCommand command) {
        AuthorizationCode code = codeRepository.consume(command.code())
                .orElseThrow(() -> new InvalidGrantException("없거나 이미 쓴 코드입니다."));

        if (code.isExpired(Instant.now())) {
            throw new InvalidGrantException("만료된 코드입니다.");
        }
        if (code.getRealm() != command.realm()) {
            // 포털에서 받은 코드를 어드민 경로로 들고 오는 길을 막는다.
            throw new InvalidGrantException("다른 realm 에서 발급된 코드입니다.");
        }
        if (!code.belongsTo(command.clientId(), command.redirectUri(), command.codeVerifier())) {
            throw new InvalidGrantException("이 코드의 임자가 아닙니다.");
        }

        return issueTokens.forPrincipal(code.getPrincipalId());
    }
}
